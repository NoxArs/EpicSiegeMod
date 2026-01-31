package funwayguy.esm.client.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.client.config.DummyConfigElement.DummyCategoryElement;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import funwayguy.esm.core.ESM;
import funwayguy.esm.core.ESM_Settings;

public class ESMGuiConfig extends GuiConfig {

    public static ArrayList<Configuration> tempConfigs = new ArrayList<Configuration>();

    public ESMGuiConfig(GuiScreen parent) {
        super(parent, GetConfigElements(), ESM_Settings.ID, false, false, ESM_Settings.Name);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<IConfigElement> GetConfigElements() {
        tempConfigs.clear();
        List<IConfigElement> list = new ArrayList<IConfigElement>();

        list.add(
            new DummyCategoryElement(
                "Default Configuration",
                "",
                getConfigElements(ESM_Settings.defConfig.getConfigFile())));

        String prefix = "";
        if (ESM.proxy.isClient()) {
            prefix = "saves/";
        }

        ArrayList<File> worldOptions = new ArrayList<File>();

        for (File file : new File(prefix).listFiles()) {
            if (file.isDirectory() && new File(file, "ESM_Options.cfg").exists()) {
                worldOptions.add(new File(file, "ESM_Options.cfg"));
            }
        }

        list.add(
            new DummyCategoryElement(
                "World Configurations",
                "epicsiegemod.config.world",
                getConfigElements(worldOptions.toArray(new File[] {}))));

        return list;
    }

    @SuppressWarnings("rawtypes")
    private static List<IConfigElement> getConfigElements(File file) {
        Configuration config = new Configuration(file, true);
        tempConfigs.add(config);
        Iterator<String> iterator = config.getCategoryNames()
            .iterator();
        List<IConfigElement> customConfigList = new ArrayList<IConfigElement>();
        while (iterator.hasNext()) {
            ConfigCategory category = config.getCategory(iterator.next());
            if (!category.isChild()) {
                customConfigList.add(new ConfigElement(category));
            }
        }

        return customConfigList;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<IConfigElement> getConfigElements(File[] files) {
        List<IConfigElement> customFileList = new ArrayList<IConfigElement>();

        for (File entry : files) {
            Configuration config = new Configuration(entry, true);
            tempConfigs.add(config);
            Iterator<String> iterator = config.getCategoryNames()
                .iterator();
            List<IConfigElement> customConfigList = new ArrayList<IConfigElement>();
            while (iterator.hasNext()) {
                ConfigCategory category = config.getCategory(iterator.next());
                if (!category.isChild()) {
                    customConfigList.add(new ConfigElement(category));
                }
            }
            customFileList.add(
                new DummyCategoryElement(
                    entry.getParentFile()
                        .getName(),
                    "null",
                    customConfigList));
        }

        return customFileList;
    }
}
