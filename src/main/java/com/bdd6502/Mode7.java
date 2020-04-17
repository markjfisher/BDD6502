package com.bdd6502;

public class Mode7 extends DisplayLayer {
    int busContention = 0;
    byte screenData[] = new byte[0x2000];
    byte tiles[] = new byte[0x4000];
    int dx = 0, dxy = 0;
    int dy = 0, dyx = 0;
    int backgroundColour = 0;

    @Override
    public void writeData(int address, int addressEx, byte data) {
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa000) {
            dx = (dx & 0xffff00) | (data & 0xff);
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa001) {
            dx = (dx & 0xff00ff) | ((data & 0xff)<<8);
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa002) {
            dx = (dx & 0x00ffff) | ((data & 0xff)<<16);
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa003) {
            dxy = (dxy & 0xffff00) | (data & 0xff);
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa004) {
            dxy = (dxy & 0xff00ff) | ((data & 0xff)<<8);
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa005) {
            dxy = (dxy & 0x00ffff) | ((data & 0xff)<<16);
        }

        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa006) {
            dy = (dy & 0xffff00) | (data & 0xff);
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa007) {
            dy = (dy & 0xff00ff) | ((data & 0xff)<<8);
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa008) {
            dy = (dy & 0x00ffff) | ((data & 0xff)<<16);
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa009) {
            dyx = (dyx & 0xffff00) | (data & 0xff);
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa00a) {
            dyx = (dyx & 0xff00ff) | ((data & 0xff)<<8);
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa00b) {
            dyx = (dyx & 0x00ffff) | ((data & 0xff)<<16);
        }

        if (DisplayBombJack.addressExActive(addressEx , 0x01) && address == 0xa014) {
            backgroundColour = data & 0xff;
        }

        // This selection logic is because the actual address line is used to select the memory, not a decoder
        if (DisplayBombJack.addressExActive(addressEx , 0x08) && (address & 0x2000) > 0) {
            busContention = display.getBusContentionPixels();
            screenData[address & 0x1fff] = data;
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x08) && (address & 0x4000) > 0) {
            busContention = display.getBusContentionPixels();
            tiles[address & 0x1fff] = data;
        }
        if (DisplayBombJack.addressExActive(addressEx , 0x08) && (address & 0x8000) > 0) {
            busContention = display.getBusContentionPixels();
            tiles[(address & 0x1fff) + 0x2000] = data;
        }
    }

    @Override
    public int calculatePixel(int displayH, int displayV, boolean _hSync, boolean _vSync) {
        // Adjust for the extra timing
        if (displayH >= 0x180) {
            displayH -= 0x80;
        }
        // Adjust to match real hardware
        displayH = displayH & 0xff;
        // Add scrolls and clamp
        displayH &= 0x3ff;
        displayV &= 0x3ff;
        int index = ((displayH>>4) & 0x7f) + (((displayV>>4) & 0x3f) * 0x80);
        int theChar = (screenData[index]) & 0xff;
//        System.out.println(displayH + " " + displayV + " Chars index: " + Integer.toHexString(index) + " char " + Integer.toHexString(theChar));
        displayH &= 0x0f;
        displayV &= 0x0f;
        // Include flips
        if ((theChar & 0x40) > 0) {
            displayH = 0x0f-displayH;
        }
        if ((theChar & 0x80) > 0) {
            displayV = 0x0f-displayV;
        }
        theChar &= 0x3f;
        int finalPixel = tiles[(theChar << 8) + (displayV * 0x10) + displayH];
        if (finalPixel == 0) {
            finalPixel = backgroundColour;
        }
        if (busContention > 0) {
            finalPixel = display.getRandomColouredPixel();
        }
        if (busContention > 0) {
            busContention--;
        }
        return finalPixel;
    }
}
