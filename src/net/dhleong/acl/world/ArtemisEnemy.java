package net.dhleong.acl.world;

public class ArtemisEnemy extends BaseArtemisShip {
    
    // elite enemy bits
    public static final int ELITE_INVIS_TO_MAIN_SCREEN  = 1;
    public static final int ELITE_INVIS_TO_SCIENCE      = 2;
    public static final int ELITE_INVIS_TO_TACTICAL     = 4;
    public static final int ELITE_CLOAKING              = 8;
    public static final int ELITE_HET                   = 16;
    public static final int ELITE_WARP                  = 32;
    public static final int ELITE_TELEPORT              = 64;
    
    private boolean mIsScanned = false;
    
    public ArtemisEnemy(int objId, String name, int hullId) {
        super(objId, name, hullId);
        
    }
    
    @Override
    public int getType() {
        return TYPE_ENEMY;
    }
    
    public boolean isScanned() {
        return mIsScanned;
    }
    
    public void setScanned() {
        mIsScanned = true;
    }
    
    @Override
    public void updateFrom(ArtemisPositionable eng) {
        super.updateFrom(eng);
        
        // it SHOULD be an ArtemisEnemy
        if (eng instanceof ArtemisEnemy) {
            ArtemisEnemy cast = (ArtemisEnemy)eng;
            if (cast.isScanned())
                setScanned();
        }
        
    }

    @Override
    public String toString() {
        return String.format("[ENEMY:%s:%d:%c]%s", 
                mName, 
                mHullId,
                mIsScanned ? 'S' : '_',
                super.toString());
    }
}
