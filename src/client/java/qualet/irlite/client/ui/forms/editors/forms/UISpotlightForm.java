package qualet.irlite.client.ui.forms.editors.forms;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import qualet.irlite.client.ui.forms.editors.panels.UISpotlightFormPanel;
import qualet.irlite.forms.SpotlightForm;

public class UISpotlightForm extends UIForm<SpotlightForm>
{
    public UISpotlightForm()
    {
        super();

        this.defaultPanel = new UISpotlightFormPanel(this);

        this.registerPanel(this.defaultPanel, IKey.constant("Spotlight"), Icons.FRUSTUM);
        this.registerDefaultPanels();
    }
}
