package app.thelasthit.nrt

import app.thelasthit.nrt.data.DataGraph

/** The app on an in-memory database, so each test starts empty. Set in robolectric.properties. */
class TestApp : LastHitApp() {
    override fun createGraph(): DataGraph = DataGraph.inMemory(this)
}
