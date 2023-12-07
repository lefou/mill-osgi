package de.tobiasroeser.mill.osgi

import mill.{Agg, T}
import mill.api.PathRef
import mill.scalalib.JavaModule

trait OsgiBundleModulePlatform extends JavaModule {

  override def compileClasspath: T[Agg[PathRef]] = T {
    // restore pre-Mill-0.11.0-M8 behavior
    // Mill 0.11.0-M8 uses transitiveCompileClasspath instead of transitiveLocalClass
    // which does not include the resources
    transitiveLocalClasspath() ++
      compileResources() ++
      unmanagedClasspath() ++
      resolvedIvyDeps()
  }

}
