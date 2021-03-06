package net.dhleong.acl.world;

/**
 * This is some "generic mesh" in the world
 * 
 * @author dhleong
 *
 */
public class ArtemisMesh extends BaseArtemisObject {

    private String mMesh;
    private String mTex;
    private int mColor;
    private float mShieldsFront;
    private float mShieldsRear;

    public ArtemisMesh(int objId, String name) {
        super(objId, name);
    }

    @Override
    public int getType() {
        return ArtemisObject.TYPE_MESH;
    }
    
    public String getMesh() {
        return mMesh;
    }
    
    public void setMesh(String path) {
        mMesh = path;
    }
    
    public String getTexture() {
        return mTex;
    }
    
    public void setTexture(String path) {
        mTex = path;
    }
    
    public int getColor() {
        return mColor;
    }
    
    public float getShieldsFront() {
        return mShieldsFront;
    }
    
    public float getShieldsRear() {
        return mShieldsRear;
    }
    
    public void setARGB(int a, int r, int g, int b) {
        mColor = 0;
        mColor |= ((a & 0xff) << 24);
        mColor |= ((r & 0xff) << 16);
        mColor |= ((g & 0xff) << 8);
        mColor |= (b & 0xff);
    }
    
    public void setARGB(float a, float r, float g, float b) {
        setARGB(
            (int)(255 * a),
            (int)(255 * r), 
            (int)(255 * g), 
            (int)(255 * b)
        );
    }

    @Override
    public void updateFrom(ArtemisPositionable other) {
        super.updateFrom(other);
        
        ArtemisMesh m = (ArtemisMesh) other;
        if (m.mShieldsFront != -1)
            mShieldsFront = m.mShieldsFront;
        
        if (m.mShieldsRear != -1)
            mShieldsRear = m.mShieldsRear;
    }

    @Override
    public String toString() {
        return String.format("%s%s[%s]<%.2f, %.2f>\n{%s}{%s}", 
                getName(), super.toString(),
                Integer.toHexString(mColor),
                mShieldsFront, mShieldsRear,
                mMesh, mTex);
    }

    public void setFakeShields(float shieldsFront, float shieldsRear) {
        mShieldsFront = shieldsFront;
        mShieldsRear = shieldsRear;
    }
}
