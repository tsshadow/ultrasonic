-dontobfuscate

### Don't remove subsonic api serializers/entities
-keep class org.moire.ultrasonic.api.subsonic.** { *; }

## Don't remove the domain models
-keep class org.moire.ultrasonic.domain.** { *; }

## Don't remove the imageloader
-keep class org.moire.ultrasonic.imageloader.** { *; }
-keep class org.moire.ultrasonic.provider.AlbumArtContentProvider { *; }

## Don't remove NowPlayingFragment
-keep class org.moire.ultrasonic.fragment.NowPlayingFragment { *; }
