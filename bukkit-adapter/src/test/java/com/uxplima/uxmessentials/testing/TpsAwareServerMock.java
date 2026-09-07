package com.uxplima.uxmessentials.testing;

import org.mockbukkit.mockbukkit.ServerMock;

/**
 * A {@link ServerMock} that answers {@code getTPS()}.
 *
 * <p>MockBukkit declares the method and throws {@code UnimplementedOperationException} from it. That
 * exception extends {@code TestAbortedException}, so JUnit records a test that reaches it as skipped rather
 * than failed: the test reports green while every assertion after the call is never evaluated. Filling the
 * hole with a fixed reading is what lets the health read-out under test actually run.
 *
 * <p>The reading is fixed rather than simulated. A test that asserts on live TPS would assert on the machine
 * it happens to run on, which is not a property of the code.
 */
// ServerMock overrides Server#getBanList(Type) without its type parameter, so any subclass inherits the
// raw-override warning. It is upstream's and cannot be fixed here, and -Werror would otherwise refuse the
// subclass outright.
@SuppressWarnings("unchecked")
public class TpsAwareServerMock extends ServerMock {

    /** A healthy server: twenty ticks a second over one, five and fifteen minutes. */
    private double[] tps = {20.0, 20.0, 20.0};

    @Override
    public double[] getTPS() {
        return tps.clone();
    }

    /** Set the three averages the server reports, for a test that needs a server under load. */
    public void setTps(double oneMinute, double fiveMinutes, double fifteenMinutes) {
        this.tps = new double[] {oneMinute, fiveMinutes, fifteenMinutes};
    }
}
