package funwayguy.esm.core;

public class DimSettings {

    public double dmgMult = 1.0F;
    public double spdMult = 1.0F;
    public double hpMult = 1.0F;
    public double knockResist = 1.0F;

    public DimSettings(double hpMult, double dmgMult, double spdMult, double knockResist) {
        this.hpMult = hpMult - 1D;
        this.dmgMult = dmgMult - 1D;
        this.spdMult = spdMult - 1D;
        this.knockResist = knockResist - 1D;
    }
}
