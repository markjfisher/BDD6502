package com.bdd6502;

import javax.sound.sampled.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

// Any comment with "HW:" draws attention to hardware design considerations
public class AudioExpansion extends MemoryBus {
    int addressRegisters = 0x8000, addressExRegisters = 0x01;
    int addressExSampleBank = 0x04;

    // 2MHz /8 /8 gives ample time to latch, add, select, apply volume for 8 voices, accumulate and output in a cyclic pattern
    public static final int sampleRate = 31250;
    static final int numVoices = 4;
    public static final int voiceSize = 11;
    static final int samplesToMix = 8;
    public static final int counterShift = 12;
    public static final int counterShiftValue = 1<<counterShift;
    public static final int counterShiftMask = counterShiftValue - 1;
    byte[] sampleBuffer = new byte[samplesToMix];

    SourceDataLine line = null;

    byte sampleRAM[] = new byte[0x10000];

    int voiceInternalCounter[] = new int[numVoices];    // Not addressable
    boolean voiceInternalChooseLoop[] = new boolean[numVoices];    // Not addressable

    byte voicesActiveMask = 0;
    byte voicesLoopMask = 0;
    // Each voice is stored linearly in the address space, with 8 bytes per voice:
    // volume
    // voiceAddressLo , voiceAddressHi
    // voiceLengthLo , voiceLengthHi
    // voiceRateLo , voiceRateHi
    // spare
    int voiceVolume[] = new int[numVoices];
    int voiceAddress[] = new int[numVoices];
    int voiceLength[] = new int[numVoices];
    int voiceRate[] = new int[numVoices];
    int voiceLoopAddress[] = new int[numVoices];
    int voiceLoopLength[] = new int[numVoices];

    public static int calculateRateFromFrequency(int frequency) {
        return (AudioExpansion.counterShiftValue * frequency) / AudioExpansion.sampleRate;
    }

    public AudioExpansion() {
    }

    public AudioExpansion(int addressRegisters, int addressExSampleBank) {
        assertThat(addressRegisters, is(greaterThanOrEqualTo(0x8000)));
        assertThat(addressRegisters, is(lessThan(0xc000)));
        assertThat(addressRegisters & 0x7ff, is(equalTo(0x00)));
        this.addressRegisters = addressRegisters;
        this.addressExSampleBank = addressExSampleBank;
    }

    public void start() {
        // https://docs.oracle.com/javase/tutorial/sound/playing.html
        AudioFormat format = new AudioFormat(sampleRate, 8, 1, false, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        try {
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, sampleRate / 60);    // At 60 fps we want this many samples
            line.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        line.close();
    }

    @Override
    void writeData(int address, int addressEx, byte data) {
        // No contention, this will use latches, many of them
        if (MemoryBus.addressActive(addressEx, addressExRegisters) && (address >= addressRegisters) && (address < (addressRegisters + (numVoices * voiceSize)))) {
            int voice = (address - addressRegisters) / voiceSize;
            int voiceSection = (address - addressRegisters) % voiceSize;
            switch (voiceSection) {
                case 0:
                    voiceVolume[voice] = (data & 0xff);
                    break;
                case 1:
                    voiceAddress[voice] = (voiceAddress[voice] & 0xff00) | (data & 0xff);
                    break;
                case 2:
                    voiceAddress[voice] = (voiceAddress[voice] & 0x00ff) | ((data & 0xff) << 8);
                    break;
                case 3:
                    voiceLength[voice] = (voiceLength[voice] & 0xff00) | (data & 0xff);
                    break;
                case 4:
                    voiceLength[voice] = (voiceLength[voice] & 0x00ff) | ((data & 0xff) << 8);
                    break;
                case 5:
                    voiceRate[voice] = (voiceRate[voice] & 0xff00) | (data & 0xff);
                    break;
                case 6:
                    voiceRate[voice] = (voiceRate[voice] & 0x00ff) | ((data & 0xff) << 8);
                    break;
                case 7:
                    voiceLoopAddress[voice] = (voiceLoopAddress[voice] & 0xff00) | (data & 0xff);
                    break;
                case 8:
                    voiceLoopAddress[voice] = (voiceLoopAddress[voice] & 0x00ff) | ((data & 0xff) << 8);
                    break;
                case 9:
                    voiceLoopLength[voice] = (voiceLoopLength[voice] & 0xff00) | (data & 0xff);
                    break;
                case 10:
                    voiceLoopLength[voice] = (voiceLoopLength[voice] & 0x00ff) | ((data & 0xff) << 8);
                    break;
                default:
                    // Do nothing
                    break;
            }
        }

        if (MemoryBus.addressActive(addressEx, addressExRegisters) && address == addressRegisters + (numVoices * voiceSize)) {
            voicesLoopMask = data;
        }
        if (MemoryBus.addressActive(addressEx, addressExRegisters) && address == addressRegisters + (numVoices * voiceSize) + 1) {
            voicesActiveMask = data;
            for (int i = 0 ; i < numVoices ; i++) {
                if ((voicesActiveMask & (1 << i)) == 0) {
                    // HW: Reset the latch on low. voiceInternalCounter is 24 bits
                    voiceInternalCounter[i] = 0;
                    voiceInternalChooseLoop[i] = false;
                }
            }
        }

        // HW: Full 64K for sample memory, must use a proper selector
        // Some contention here as this uses banks of RAM
        if (MemoryBus.addressActive(addressEx, 0x04)) {
            busContention = 8;
            sampleRAM[address] = data;
        }
    }

    void calculateSamples() {
        if (line.available() < sampleBuffer.length) {
            return;
        }
        for (int i = 0 ; i < sampleBuffer.length ; i++) {
            ageContention();

            int accumulatedSample = 0;
            for (int voice = 0 ; voice < numVoices ; voice++) {
                if ((voicesActiveMask & (1 << voice)) > 0) {
                    int address;
                    if (voiceInternalChooseLoop[voice]) {
                        // HW: Note accuracy shifting is just address line selection
                        address = voiceLoopAddress[voice] + (voiceInternalCounter[voice] >> counterShift);
                    } else {
                        // HW: Note accuracy shifting is just address line selection
                        address = voiceAddress[voice] + (voiceInternalCounter[voice] >> counterShift);
                    }

                    int sample = sampleRAM[address & 0xffff] & 0xff;
                    // HW: This will be implemented with a 0x10000 byte ROM containing a multiply/divide lookup table
                    sample = (sample * voiceVolume[voice]) / 255;

                    accumulatedSample += sample;

                    // HW: Note add is clocked after the sample read
                    voiceInternalCounter[voice] += voiceRate[voice];

                    // HW: Note selective comparison is just address line selection
                    if (voiceInternalChooseLoop[voice]) {
                        if (((voiceInternalCounter[voice] >> counterShift) & 0xffff) >= voiceLoopLength[voice]) {
                            // HW: Note selective reset of only some adders when length is reached
                            voiceInternalCounter[voice] = voiceInternalCounter[voice] & counterShiftMask;
                        }
                    } else {
                        if (((voiceInternalCounter[voice] >> counterShift) & 0xffff) >= voiceLength[voice]) {
                            if ((voicesLoopMask & (1 << voice)) > 0) {
                                voiceInternalChooseLoop[voice] = true;
                                // HW: Note selective reset of only some adders when length is reached
                                voiceInternalCounter[voice] = voiceInternalCounter[voice] & counterShiftMask;
                            } else {
                                // HW: Reset the latch for this specific voice
                                voicesActiveMask = (byte) (voicesActiveMask & ~(1 << voice));
                            }
                        }
                    }

                } else {
                    // HW: Add 0x80 as the middle part of 8 bit unsigned samples for inactive channels
                    accumulatedSample += 0x80;
                }
            }

            // HW: Note voice division is just address line selection
            // HW: There will need to be an overflow and upper clamp applied
            accumulatedSample = accumulatedSample / numVoices;
            if (accumulatedSample > 255) {
                accumulatedSample = 255;
            }
            sampleBuffer[i] = (byte)(accumulatedSample & 0xff);
        }
        line.write(sampleBuffer,0,sampleBuffer.length);
    }
}