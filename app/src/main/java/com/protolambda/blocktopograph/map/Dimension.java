package com.protolambda.blocktopograph.map;


import com.protolambda.blocktopograph.map.renderer.MapType;

import java.util.HashMap;
import java.util.Map;

public enum Dimension {

    OVERWORLD(0, "overworld", "Overworld", 16, 16, 128, 1, MapType.SATELLITE),
    NETHER(1, "nether", "Nether", 16, 16, 128, 8, MapType.NETHER),
    END(2, "end", "End", 16, 16, 128, 1, MapType.SATELLITE);//mcpe: SOON^TM /jk

    public final int id;
    public final int chunkW, chunkL, chunkH;
    public final int dimensionScale;
    public final String dataName, name;
    public final MapType defaultMapType;

    Dimension(int id, String dataName, String name, int chunkW, int chunkL, int chunkH, int dimensionScale, MapType defaultMapType){
        this.id = id;
        this.dataName = dataName;
        this.name = name;
        this.chunkW = chunkW;
        this.chunkL = chunkL;
        this.chunkH = chunkH;
        this.dimensionScale = dimensionScale;
        this.defaultMapType = defaultMapType;
    }

    private static Map<String, Dimension> dimensionMap = new HashMap<>();

    static {
        for(Dimension dimension : Dimension.values()){
            dimensionMap.put(dimension.dataName, dimension);
        }
    }

    public static Dimension getDimension(String dataName){
        if(dataName == null) return null;
        return dimensionMap.get(dataName.toLowerCase());
    }

    public static Dimension getDimension(int id){
        for(Dimension dimension : values()){
            if(dimension.id == id) return dimension;
        }
        return null;
    }

}
