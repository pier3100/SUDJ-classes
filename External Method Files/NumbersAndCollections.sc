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

+ Float {
    // The default asStringPrec uses "%g". This method allows us to instead
    // use a "%f" style output.
    //
    // Example:
    //
    //  asStringPrecF(-2.08008714253083e-07) == "-0.000000208008714"
    //
    asStringPrecF { |prec = 14|
        var as, c, pre;
        as = this.abs.asString;
        // Pass through values that don't have an negative exponent
        if (as.containsi("e-").not) { ^this.asStringPrec(prec) } {
            // Otherwise split on the exponent
            c = as.split($e);
            c[0].remove($.);
            pre = if(this < 0, "-0.", "0.");
            ^[pre,
                "0".extend(c[1].asInteger.neg - 1, $0),
                c[0]].join[..(prec + pre.size - 1)]
        }
    }
}