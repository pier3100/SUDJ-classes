+ PathName {
    asPathName {
        ^this;
    }
}

+ String {
    /* asCompileString {
        ^this.replace('\\','\\\\');
    }

    asCompileStringCallPrimitive {
        _String_AsCompileString
		^this.primitiveFailed
    } */

    formatPlain {
        ^this.replace("&amp;","&");
    }

    barcodeId2EAN13 {|type|
        // type should be a number between 0 and 4, either as a number or as a string 
        var provisionalEan, checkDigit;
        provisionalEan = type.asString ++ "0" ++ this;
        checkDigit = (
            10 - ((1 * provisionalEan[0].asString.asInteger)  +
            (3 * provisionalEan[1].asString.asInteger)  +
            (1 * provisionalEan[2].asString.asInteger)  +
            (3 * provisionalEan[3].asString.asInteger)  +
            (1 * provisionalEan[4].asString.asInteger)  +
            (3 * provisionalEan[5].asString.asInteger)  +
            (1 * provisionalEan[6].asString.asInteger)  +
            (3 * provisionalEan[7].asString.asInteger)  +
            (1 * provisionalEan[8].asString.asInteger)  +
            (3 * provisionalEan[9].asString.asInteger)  +
            (1 * provisionalEan[10].asString.asInteger)  +
            (3 * provisionalEan[11].asString.asInteger)).mod(10)
        );    
        if(checkDigit == 10){ checkDigit = 0; };    
        ^(provisionalEan ++ "0" ++ checkDigit.asString);
    }

    asHexAscii {
        var output;
        for(0, (this.size - 1)){ |i|
            output = output ++ this.at(i).ascii.asHexString(2);
        }
        ^output;
    }
}

+ Nil {
    formatPlain {
        ^this;
    }
}

+ Symbol {
    asCompileString {
        ^this.asString.replace("\\","\\\\").asSymbol.asCompileStringCallPrimitive;
    }

    asCompileStringCallPrimitive {
        _ObjectCompileString
		^String.streamContents({ arg stream; this.storeOn(stream); });
    }
}