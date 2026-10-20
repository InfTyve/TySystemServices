package android.os;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(ServiceManager.class)
public class ServiceManagerHidden {
    public static IBinder getService(String name) {
        throw new RuntimeException("Stub!");
    }
}
