/**
 * DO NOT ADD COMPLEX LOGIC HERE
 *
 * This is a hand-built gradle file which is used to speed up development,
 * it is not the build file which is used during a firmware build, so any
 * complex logic added here will not be executed in a firmware build.
 */

plugins {
    id("io.snappautomotive.android-compose-app")
}

android {
    namespace = "com.android.car.updatable.rro"
}
