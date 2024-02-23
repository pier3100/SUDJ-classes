DjFilter {
    *ar { |input, position|
        var lowPass, highPass, output;
        // low pass
        lowPass = VadimFilter.ar(input, position.linexp(0, 0.5, 50, 20000), resonance: 0.0, type: 1);
        //lowPass = MoogLadder.ar(input, position.linexp(0, 0.5, 50, 20000));
        output = XFade2.ar(lowPass, input, position.linlin(0.4, 0.5, -1, 1));

        //highPass = RHPF.ar(input, position.linexp(0.5, 1, 50, 20000));
        highPass = VadimFilter.ar(input, position.linexp(0.5, 1, 50, 20000), resonance: 0.0, type: 5);
        output = XFade2.ar(output, highPass, position.linlin(0.5, 0.6, -1, 1));
        ^output;
    }
}