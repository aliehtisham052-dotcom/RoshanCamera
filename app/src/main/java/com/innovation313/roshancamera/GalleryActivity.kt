package com.innovation313.roshancamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshancamera.databinding.ActivityGalleryBinding
import com.innovation313.roshancamera.storage.PhotoStore
import com.innovation313.roshancamera.storage.ThumbnailLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Photos this app has written, newest first.
 *
 * Reads straight from MediaStore rather than keeping its own copy of the list:
 * if a user deletes a photo in their gallery app it disappears here too, with
 * no sync step to get wrong.
 */
class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val photoStore by lazy { PhotoStore(this) }
    private val thumbnails by lazy { ThumbnailLoader(this) }
    private lateinit var adapter: PhotoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PhotoAdapter(lifecycleScope, thumbnails, ::share)
        binding.grid.layoutManager = GridLayoutManager(this, SPAN_COUNT)
        binding.grid.adapter = adapter
        binding.back.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val photos = photoStore.list()
            adapter.submit(photos)
            binding.empty.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun share(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_photo)))
    }

    private companion object {
        const val SPAN_COUNT = 3
        const val THUMBNAIL_PX = 320
    }
}

private class PhotoAdapter(
    private val scope: LifecycleCoroutineScope,
    private val thumbnails: ThumbnailLoader,
    private val onClick: (Uri) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.Holder>() {

    private val items = mutableListOf<Uri>()

    fun submit(uris: List<Uri>) {
        val previousSize = items.size
        items.clear()
        notifyItemRangeRemoved(0, previousSize)
        items += uris
        notifyItemRangeInserted(0, items.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return Holder(view as ImageView)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val uri = items[position]
        // Recycled views can still be waiting on an earlier row's bitmap; cancel
        // it or the wrong photo lands in the cell.
        holder.pending?.cancel()
        holder.image.setImageDrawable(null)
        holder.image.setOnClickListener { onClick(uri) }
        holder.pending = scope.launch {
            thumbnails.load(uri, THUMBNAIL_PX)?.let(holder.image::setImageBitmap)
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.pending?.cancel()
        holder.pending = null
    }

    override fun getItemCount(): Int = items.size

    class Holder(val image: ImageView) : RecyclerView.ViewHolder(image) {
        var pending: Job? = null
    }

    private companion object {
        const val THUMBNAIL_PX = 320
    }
}
