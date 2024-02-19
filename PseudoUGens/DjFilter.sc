DjFilter {
    *ar { |input, position|
        var lowPass, highPass, output;
        // low pass
        lowPass = MoogLadder.ar(input, position.linexp(0, 0.5, 50, 20000));
        output = XFade2.ar(lowPass, input, position.linlin(0.1, 0.5, -1, 1));

        highPass = RHPF.ar(input, position.linexp(0.5, 1, 50, 20000));
        output = XFade2.ar(output, highPass, position.linlin(0.5, 0.9, -1, 1));
        ^output;
    }
}