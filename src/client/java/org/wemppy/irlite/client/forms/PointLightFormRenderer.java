package org.wemppy.irlite.client.forms;

import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import org.joml.Vector3d;
import org.wemppy.irlite.client.light.IRLightPositionResolver;
import org.wemppy.irlite.client.light.LightRegistry;
import org.wemppy.irlite.forms.PointLightForm;

public class PointLightFormRenderer extends AbstractLightFormRenderer<PointLightForm>
{
    public PointLightFormRenderer(PointLightForm form)
    {
        super(form);
    }

    @Override
    protected Color lightColor()
    {
        return this.form.color.get();
    }

    @Override
    protected Icon icon()
    {
        return Icons.LIGHT;
    }

    @Override
    protected void renderGuide(FormRenderingContext context, Color color)
    {
        LightGuideRenderer.renderPointLight(context.stack, color, this.form.radius.get());
    }

    @Override
    protected void registerLight(FormRenderingContext context)
    {
        Vector3d p = IRLightPositionResolver.resolve(context);
        Color c = this.form.color.get();
        LightRegistry.registerPoint(
            (float) p.x, (float) p.y, (float) p.z,
            c.r, c.g, c.b,
            this.form.intensity.get(), this.form.radius.get(),
            this.form.entitiesOnly.get(),
            this.form.anisotropy.get(), this.form.vlDensity.get(), this.form.beamStrength.get(),
            System.identityHashCode(this.form)
        );
    }
}
