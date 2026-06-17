package qualet.irlite.client;

import net.fabricmc.api.ClientModInitializer;
import org.qualet.irl.patcher.Patcher;
import qualet.irlite.client.patcher.BbsPatcherHost;

public class IrliteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Wire the shared patcher core to BBS (UIUtils + Iris + bundled assets).
        Patcher.install(new BbsPatcherHost());
    }
}
