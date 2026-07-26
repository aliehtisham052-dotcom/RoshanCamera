# Roshan Camera — release shrinking rules.
# Keep line numbers so crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ZXing's core encoder is reached only through QRCodeWriter; R8 keeps what it
# can see, and nothing here is loaded reflectively, so no extra keeps are needed.
# This file stays as the place to record that conclusion rather than to guess later.
