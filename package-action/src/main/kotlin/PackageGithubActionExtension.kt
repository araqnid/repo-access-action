import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

@Suppress("LeakingThis")
abstract class PackageGithubActionExtension {
    abstract val useNcc: Property<Boolean>
    abstract val nccVersion: Property<String>
    abstract val minify: Property<Boolean>
    abstract val v8cache: Property<Boolean>
    abstract val target: Property<String>
    abstract val sourceMap: Property<Boolean>
    abstract val externalModules: SetProperty<String>

    init {
        minify.convention(false)
        v8cache.convention(false)
        sourceMap.convention(false)
        useNcc.convention(true)
        nccVersion.convention("latest")
    }
}
