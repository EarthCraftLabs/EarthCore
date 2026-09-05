package de.mecrytv.earthcore.config

internal object CallerLookup {

    fun outside(owner: Class<*>): Class<*>? =
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk { frames ->
            frames.map { it.declaringClass }
                .filter { it != CallerLookup::class.java && !belongsTo(it, owner) }
                .findFirst()
                .orElse(null)
        }

    private fun belongsTo(candidate: Class<*>, owner: Class<*>): Boolean =
        candidate.name == owner.name || candidate.name.startsWith(owner.name + "$")
}
