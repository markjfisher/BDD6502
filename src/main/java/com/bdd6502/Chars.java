package com.bdd6502;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class Chars extends DisplayLayer {
    int addressScreen = 0x9000, addressExScreen = 0x01;
    int addressColour = 0x9400, addressExColour = 0x01;
    int addressPlane0 = 0x2000, addressExPlane0 = 0x20;
    int addressPlane1 = 0x4000, addressExPlane1 = 0x20;
    int addressPlane2 = 0x8000, addressExPlane2 = 0x20;
    byte screenData[] = new byte[0x400];
    byte colourData[] = new byte[0x400];
    byte plane0[] = new byte[0x2000];
    byte plane1[] = new byte[0x2000];
    byte plane2[] = new byte[0x2000];
    int latchedDisplayV = 0;

    public Chars() {
    }

    public Chars(int addressScreen, int addressExPlane0) {
        assertThat(addressScreen, is(greaterThanOrEqualTo(0x8000)));
        assertThat(addressScreen, is(lessThan(0xc000)));
        assertThat(addressScreen & 0x7ff, is(equalTo(0x00)));
        this.addressScreen = addressScreen;
        this.addressColour = addressScreen + 0x400;
        this.addressExPlane0 = addressExPlane0;
        this.addressExPlane1 = addressExPlane0;
        this.addressExPlane2 = addressExPlane0;
    }

    @Override
    public void writeData(int address, int addressEx, byte data) {
        if (DisplayBombJack.addressActive(addressEx, addressExScreen) && address >= addressScreen && address < (addressScreen + 0x400)) {
            busContention = display.getBusContentionPixels();
            screenData[address & 0x3ff] = data;
        }
        if (DisplayBombJack.addressActive(addressEx, addressExColour) && address >= addressColour && address < (addressColour + 0x400)) {
            busContention = display.getBusContentionPixels();
            colourData[address & 0x3ff] = data;
        }

        // This selection logic is because the actual address line is used to select the memory, not a decoder
        if (DisplayBombJack.addressActive(addressEx, addressExPlane0) && DisplayBombJack.addressActive(address, addressPlane0)) {
            busContention = display.getBusContentionPixels();
            plane0[address & 0x1fff] = data;
        }
        if (DisplayBombJack.addressActive(addressEx, addressExPlane0) && DisplayBombJack.addressActive(address, addressPlane1)) {
            busContention = display.getBusContentionPixels();
            plane1[address & 0x1fff] = data;
        }
        if (DisplayBombJack.addressActive(addressEx, addressExPlane0) && DisplayBombJack.addressActive(address, addressPlane2)) {
            busContention = display.getBusContentionPixels();
            plane2[address & 0x1fff] = data;
        }
    }

    @Override
    public int calculatePixel(int displayH, int displayV, boolean _hSync, boolean _vSync) {
        if ((displayH & 0x188) == 0) {
            latchedDisplayV = displayV;
        }
        // Adjust for the extra timing
        if (displayH >= 0x180) {
            displayH -= 0x80;
        }
        displayH = displayH & 0xff;
        // -1 to match the real hardware
        int index = (((displayH >> 3) - 1) & 0x1f) + (((latchedDisplayV >> 3) & 0x1f) * 0x20);
        int theChar = (screenData[index]) & 0xff;
//        System.out.println(displayH + " " + latchedDisplayV + " Chars index: " + Integer.toHexString(index) + " char " + Integer.toHexString(theChar));
        byte theColour = colourData[index];
        // Include extra chars from the colour
        theChar |= (theColour & 0x30) << 4;
        displayH &= 0x07;
        displayH = 7 - displayH;
        int latchedDisplayV2 = latchedDisplayV;
        latchedDisplayV2 &= 0x07;
        // Include flips
        if ((theColour & 0x40) > 0) {
            displayH = 7 - displayH;
        }
        if ((theColour & 0x80) > 0) {
            latchedDisplayV2 = 7 - latchedDisplayV2;
        }
        int pixelPlane0 = plane0[(theChar << 3) + latchedDisplayV2] & (1 << displayH);
        int pixelPlane1 = plane1[(theChar << 3) + latchedDisplayV2] & (1 << displayH);
        int pixelPlane2 = plane2[(theChar << 3) + latchedDisplayV2] & (1 << displayH);
        int finalPixel = 0;
        if (pixelPlane0 > 0) {
            finalPixel |= 1;
        }
        if (pixelPlane1 > 0) {
            finalPixel |= 2;
        }
        if (pixelPlane2 > 0) {
            finalPixel |= 4;
        }
        finalPixel |= ((theColour & 0x0f) << 3);
        return getByteOrContention(finalPixel);
    }
}
