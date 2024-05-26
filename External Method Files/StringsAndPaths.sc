+ PathName {
    asPathName {
        ^this;
    }
}

+ String {
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
}

+ Nil {
    formatPlain {
        ^this;
    }
}