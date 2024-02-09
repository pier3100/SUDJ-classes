Ones : Array {
    *new { arg size;
        ^Array.fill(size,1);
    }
} 

Zeros : Array {
    *new { arg size;
        ^Array.fill(size,0);
    }
} 

+ Array {
    *zeros { |size|
        ^Array.fill(size,0);
    }

    *ones { |size|
        ^Array.fill(size,1);
    }
    *fTrue { |size|
        ^Array.fill(size,true);
    }

    *fFalse { |size|
        ^Array.fill(size,false);
    }

    setToZero {
        this.do( {|item| item = 0 }); // this way we do not overwrite the play object itself; so all references remain intact, but we do overwrite all content
    }
}

+ SimpleNumber {
    roundFractional { |divisor|
    // rounds the receiver to the nearest multiple of 1/divisor
    ^(this * divisor).round / divisor;
    }
}