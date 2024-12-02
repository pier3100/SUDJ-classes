+ Library {
    *addFromJSON { |path|
        var array;
        array = path.parseJSONFile.asPairs;
        for(0, (array.size / 2).asInteger){ |i|
            var value;
            value = array[i * 2 + 1].asString;
            
            if(value.every({ |item| item.isAlpha.not }) && value.any({ |item| item.isDecDigit })){ value = value.asFloat }; // convert to float if possible
            Library.put(array[i * 2].asSymbol, value);
        }
    }
}