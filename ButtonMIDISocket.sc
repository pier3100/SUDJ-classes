AbstractMIDISocketWithFeedback {
	var <parent, <destination, <midiOut;
	
	*new { arg midiInOut, destination ... args;
		^super.new.prInit(midiInOut, destination).performList(\init, args)
	}
	
	init { }		// your class should override this
	
	clear { }		// and this

	prInit { arg midiInOut, dest;
		var midiIn;
		destination = dest;
		midiIn = midiInOut.asChannelIndex;
		midiOut = MIDIOut(midiInOut.indexOut);
		parent = MIDIPort.at(midiIn) ?? { MIDIChannel.new(midiIn) };
		parent.add(this);
	}

	free {
		parent.remove(this);
		this.clear;
	}
	
	enable {
		parent.enableSocket(this);
	}
	
	disable {
		parent.disableSocket(this);
	}
	
	// your class must also implement:
	// destination  (returns the thing being played by this socket, or this if appropriate)
	// active  (if destination is this: return true if the responder is still valid)
	// noteOn  (take action)
	// noteOff (take action)
	
}

ToggleSynthKey {
	var func;

	*new { arg synth, key;
		^super.new.init(synth, key);
	}

	init { arg synth, key;
		func = {synth.get(key,{
				arg value; synth.set(key,value.asBoolean.not)
		})}
	}

	value {
		func.value();

	}
}

SetSynthKey {
	var func;

	*new { arg synth, key;
		^super.new.init(synth, key);
	}

	init { arg synth, key;
		func = {arg value;
			synth.set(key,value)
		}
	}

	value { arg val;
		func.value(val);
	}
}

//ButtonPushMIDISocket(channel, destination, note_num) = ButtonPushMIDISocket([~m_xone, 11], SetSynthKey(~synthy,\que),5)
//destination.value should take one argument
ButtonPushMIDISocket : AbstractMIDISocket {
	var note_num;

	init { arg note_number;
		note_num = note_number;
	}

	active { ^destination.notNil }

	noteOn { arg note, vel; if(note == note_num, {destination.value(1)}) } // make sure that if statement is fead a function

	noteOff { arg note, vel; if(note == note_num, {destination.value(0)}) }

}

//ButtonOnMIDISocket(channel, destination, note_num) = ButtonOnMIDISocket([~m_xone,11],ToggleSynthKey(~synthy,\que),5)
//destination.value should take no arguments
ButtonOnMIDISocket : AbstractMIDISocket {
	var note_num;

	init { arg note_number;
		note_num = note_number;
	}

	active { ^destination.notNil }

	noteOn { arg note; if(note == note_num,destination.value()) }

	noteOff { }
}

ButtonOnOffMIDISocket : AbstractMIDISocket {
	var note_num, onPlayer, offPlayer;

	init { arg offfunc, note_number;
		onPlayer = destination;  // since destination is this, I simplify the usual syntax:
		destination = this;	  // new(channel, dest, args)  -- so you can say instead:
		offPlayer = offfunc;	  // new(channel, on_func, off_func)
		note_num = note_number;
	}

	active { ^destination.notNil }

	noteOn { arg note; if(note == note_num){onPlayer.value()} }

	noteOff {arg note; if(note == note_num){offPlayer.value()} }
}

ButtonOnMIDISockettest : AbstractMIDISocket {
	init { arg ... args;
		args[0].postln;
	}

	active { ^destination.notNil }

	noteOn { }

	noteOff { }
}


ButtonPushWithFeedbackMIDISocket : AbstractMIDISocketWithFeedback {
	var note_num;

	init { arg note_number;
		note_num = note_number;
		midiOut.noteOn(0,note_num,17);
	}

	active { ^destination.notNil }

	noteOn { arg note, vel; if(note == note_num, {destination.value(1);
	midiOut.noteOn(0,note_num,41);
	}) } // make sure that if statement is fead a function

	noteOff { arg note, vel; if(note == note_num, {destination.value(0);
	midiOut.noteOn(0,note_num,17);
	}) }

}

ButtonPatternWithFeedbackMIDISocket : AbstractMIDISocketWithFeedback {
	var note_num, eventStreamPlayer;

	init { arg note_number;
		note_num = note_number;
		eventStreamPlayer = destination.play(~c16th, quant: 1);
		eventStreamPlayer.stop;
		midiOut.noteOn(0,note_num,17);
	}

	active { ^destination.notNil }

	noteOn { arg note, vel; if(note == note_num, {eventStreamPlayer.play(quant: 1);
	midiOut.noteOn(0,note_num,41);
	}) } // make sure that if statement is fead a function

	noteOff { arg note, vel; if(note == note_num, {eventStreamPlayer.stop;
	midiOut.noteOn(0,note_num,17);
	~m_traktor.noteOn(0,68,127);
	~m_traktor.noteOff(0,68,127);
	}) }

}



/* + SequenceableCollection {
	asChannelIndex {
		var port;
		MIDIPort.init;
		port = this.at(0);
		port.isMidiInOutPair.if({
			port.indexIn.postln;
			port = MidiPort.sources.at(port.indexIn).uid;
		},{
			port.isNumber.if({
			// low numbers are not uid's, but indices to sources
				(port.abs < MIDIPort.numPorts).if({
					port = MIDIPort.sources.at(port).uid
				})
			}, {
				if(port == \all) { port = 0x80000001 };
			});
		});
		
		^MIDIChannelIndex.new(port, this.at(1).asMIDIChannelNum)
	}
} */

/*
ButtonTottleMIDISocket([~m_xone, 11], 8         ,{arg val; ~s_channel_1.set(\cross_fader_selectorL, val)}
                       channel        note_num   function to toggle (enter 1 on note_on, enter 0 note_off

{arg note_num;
			switch(note_num,
				8,{~s_channel_1.set(\cross_fader_selectorL, 1)},

		{
	arg note_num, vel; if(note_num==10,{
		~s_channel_1.get(\que,{
			arg value; ~s_channel_1.set(\que,value.asBoolean.not)})
	})


*/


/*BasicMIDISocket : AbstractMIDISocket {
	var <>onPlayer, <>offPlayer;

	init { arg offfunc;
		onPlayer = destination;  // since destination is this, I simplify the usual syntax:
		destination = this;	  // new(channel, dest, args)  -- so you can say instead:
		offPlayer = offfunc;	  // new(channel, on_func, off_func)
	}

	clear {
		onPlayer = offPlayer = nil;
	}

	active { ^onPlayer.notNil }	// since I'm the destination, I must say if I'm active or not

	noteOn { arg note, vel; onPlayer.value(note, vel) }

	noteOff { arg note, vel; offPlayer.value(note, vel) }




	AbstractMIDISocket {
	var <parent, <destination;

	*new { arg chan, destination ... args;
		^super.new.prInit(chan.asChannelIndex, destination).performList(\init, args)
	}

	init { }		// your class should override this

	clear { }		// and this

	prInit { arg ch, dest;
		destination = dest;
		parent = MIDIPort.at(ch) ?? { MIDIChannel.new(ch) };
		parent.add(this);
	}

	free {
		parent.remove(this);
		this.clear;
	}

	enable {
		parent.enableSocket(this);
	}

	disable {
		parent.disableSocket(this);
	}

	// your class must also implement:
	// destination  (returns the thing being played by this socket, or this if appropriate)
	// active  (if destination is this: return true if the responder is still valid)
	// noteOn  (take action)
	// noteOff (take action)

}

}*/