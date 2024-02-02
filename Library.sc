+ Library {
    *addFromJSON { |path|
        var array;
        array = path.parseJSONFile.asPairs;
        for(0, (array.size / 2).asInteger){ |i|
            Library.put(array.[i * 2].asSymbol,array[i * 2 + 1]);
        }
    }
}