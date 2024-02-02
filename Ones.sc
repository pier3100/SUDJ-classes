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

}