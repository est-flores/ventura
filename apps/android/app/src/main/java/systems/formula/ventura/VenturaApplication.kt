package systems.formula.ventura

import android.app.Application
import systems.formula.ventura.core.AppContainer

class VenturaApplication : Application() {
    val container by lazy { AppContainer(this) }
}
