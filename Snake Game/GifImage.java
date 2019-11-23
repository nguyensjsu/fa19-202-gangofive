import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * 
 * Class to handle GIF images for the game
 */

public class GifImage {

    private String file;
    private boolean pause;
     private GreenfootImage[] images;
     private int[] delay;
     private int currentIndex;
     private long time;

    public GifImage(String file)
    {
        this.file = file;
        pause = false;
        if(file.toLowerCase().endsWith(".gif")) {
            loadImages();
        }
        else {
            images = new GreenfootImage[] {new GreenfootImage(file)};
            delay = new int[] {1000}; // Doesn't matter, as long as it's not zero
            currentIndex = 0;
            time = System.currentTimeMillis();
        }
    }

    public List<GreenfootImage> getImages()
    {
        ArrayList<GreenfootImage> images = new ArrayList<GreenfootImage>(this.images.length);
        for(GreenfootImage image : this.images) {
            images.add(image);
        }
        return images;
    }

    public void pause()
    {
        pause = true;
    }

    public void resume()
    {
        pause = false;
        time = System.currentTimeMillis();
    }

    public boolean isRunning()
    {
        return !pause;
    }

    public GreenfootImage getCurrentImage()
    {
        long delta = System.currentTimeMillis() - time;

        while (delta >= delay[currentIndex] && !pause) {
            delta -= delay[currentIndex];
            time += delay[currentIndex];
            currentIndex = (currentIndex+1) % images.length;
        }
        return images[currentIndex];
    }

    private void loadImages()
    {
        GifDecoder decode = new GifDecoder();
        decode.read(file);
        int numFrames = decode.getFrameCount();
        if(numFrames>0) {
            images = new GreenfootImage[numFrames];
            delay = new int[numFrames];
        }
        else {
            images = new GreenfootImage[1];
            images[0] = new GreenfootImage(1, 1);
        }

        for (int i=0 ; i<numFrames ; i++) {
            GreenfootImage image = new GreenfootImage(decode.getFrame(i).getWidth(), decode.getFrame(i).getHeight());
            image.drawImage(decode.getFrame(i), 0, 0);
            delay[i] = decode.getDelay(i);
            images[i] = image;
        }
        time = System.currentTimeMillis();
    }

    private static class Rectangle
    {
        public int x;
        public int y;
        public int width;
        public int height;
        
        public Rectangle(int x, int y, int width, int height)
        {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    

    private class GifDecoder {

        public static final int STATUS_OK = 0;

        public static final int STATUS_FORMAT_ERROR = 1;

        public static final int STATUS_OPEN_ERROR = 2;

        private BufferedInputStream in;

        private int status;

        private int width; 

        private int height; 

        private boolean gctFlag; 

        private int gctSize; 

        private int loopCount = 1;

        private int[] gct; 

        private int[] lct; 

        private int[] act; 

        private int bgIndex; 

        private Color bgColor; 

        private Color lastBgColor; 

        private int pixelAspect; 

        private boolean lctFlag; 

        private boolean interlace; 

        private int lctSize; 

        private int ix, iy, iw, ih; 

        private Rectangle lastRect; 

        private GreenfootImage image; 

        private GreenfootImage lastImage; 

        private byte[] block = new byte[256]; 

        private int blockSize = 0; 

        private int dispose = 0;

        private int lastDispose = 0;

        private boolean transparency = false;

        private int delay = 0; 

        private int transIndex; 

        private static final int MaxStackSize = 4096;

        private short[] prefix;

        private byte[] suffix;

        private byte[] pixelStack;

        private byte[] pixels;

        private ArrayList<GifFrame> frames; 
        private int frameCount;

        private class GifFrame {
            public GifFrame(GreenfootImage im, int del) {
                image = im;
                delay = del;
            }
    
            private GreenfootImage image;
    
            private int delay;
        }

        private Color colorFromInt(int rgb)
        {
            int r = (rgb & 0xFF0000) >> 16;
            int g = (rgb & 0xFF00) >> 8;
            int b = (rgb & 0xFF);
            return new Color(r,g,b);
        }

        public int getDelay(int n) {
            //
            delay = -1;
            if ((n >= 0) && (n < frameCount)) {
                delay = (frames.get(n)).delay;
            }
            return delay;
        }
        
        public int read(String name) {
            status = STATUS_OK;
            InputStream resource = this.getClass().getResourceAsStream(name);
            if (resource == null) {
                name = "images/" + name;
                resource = this.getClass().getResourceAsStream(name);
                if (resource == null) {
                    throw new RuntimeException("The gif file \"" + name + "\" doesn't exist.");
                }
            }
            in = new BufferedInputStream(resource);
            status = read(in);

            return status;
        }

        protected void readImage() {
            ix = readShort(); // (sub)image position & size
            iy = readShort();
            iw = readShort();
            ih = readShort();

            int packed = read();
            lctFlag = (packed & 0x80) != 0; // 1 - local color table flag
            interlace = (packed & 0x40) != 0; // 2 - interlace flag
            // 3 - sort flag
            // 4-5 - reserved
            lctSize = 2 << (packed & 7); // 6-8 - local color table size

            if (lctFlag) {
                lct = readColorTable(lctSize); // read table
                act = lct; // make local table active
            } else {
                act = gct; // make global table active
                if (bgIndex == transIndex)
                    bgColor = colorFromInt(0);
            }
            int save = 0;
            if (transparency) {
                save = act[transIndex];
                act[transIndex] = 0; // set transparent color if specified
            }

            if (act == null) {
                status = STATUS_FORMAT_ERROR; // no color table defined
            }

            if (err())
                return;

            decodeImageData(); // decode pixel data
            skip();

            if (err())
                return;

            frameCount++;

            // create new image to receive frame data
            image = new GreenfootImage(width, height);

            setPixels(); // transfer pixel data to image

            frames.add(new GifFrame(image, delay)); // add image to frame list

            if (transparency) {
                act[transIndex] = save;
            }
            resetFrame();

        }

        protected void decodeImageData() {
            int NullCode = -1;
            int npix = iw * ih;
            int available, clear, code_mask, code_size, end_of_information, in_code, old_code, bits, code, count, i, datum, data_size, first, top, bi, pi;

            if ((pixels == null) || (pixels.length < npix)) {
                pixels = new byte[npix]; // allocate new pixel array
            }
            if (prefix == null)
                prefix = new short[MaxStackSize];
            if (suffix == null)
                suffix = new byte[MaxStackSize];
            if (pixelStack == null)
                pixelStack = new byte[MaxStackSize + 1];

            // Initialize GIF data stream decoder.

            data_size = read();
            clear = 1 << data_size;
            end_of_information = clear + 1;
            available = clear + 2;
            old_code = NullCode;
            code_size = data_size + 1;
            code_mask = (1 << code_size) - 1;
            for (code = 0; code < clear; code++) {
                prefix[code] = 0;
                suffix[code] = (byte) code;
            }

            // Decode GIF pixel stream.

            datum = bits = count = first = top = pi = bi = 0;

            for (i = 0; i < npix;) {
                if (top == 0) {
                    if (bits < code_size) {
                        // Load bytes until there are enough bits for a code.
                        if (count == 0) {
                            // Read a new data block.
                            count = readBlock();
                            if (count <= 0)
                                break;
                            bi = 0;
                        }
                        datum += (((int) block[bi]) & 0xff) << bits;
                        bits += 8;
                        bi++;
                        count--;
                        continue;
                    }

                    // Get the next code.

                    code = datum & code_mask;
                    datum >>= code_size;
                        bits -= code_size;

                        // Interpret the code

                        if ((code > available) || (code == end_of_information))
                            break;
                        if (code == clear) {
                            // Reset decoder.
                            code_size = data_size + 1;
                            code_mask = (1 << code_size) - 1;
                            available = clear + 2;
                            old_code = NullCode;
                            continue;
                        }
                        if (old_code == NullCode) {
                            pixelStack[top++] = suffix[code];
                            old_code = code;
                            first = code;
                            continue;
                        }
                        in_code = code;
                        if (code == available) {
                            pixelStack[top++] = (byte) first;
                            code = old_code;
                        }
                        while (code > clear) {
                            pixelStack[top++] = suffix[code];
                            code = prefix[code];
                        }
                        first = ((int) suffix[code]) & 0xff;

                        // Add a new string to the string table,

                        if (available >= MaxStackSize)
                            break;
                        pixelStack[top++] = (byte) first;
                        prefix[available] = (short) old_code;
                        suffix[available] = (byte) first;
                        available++;
                        if (((available & code_mask) == 0) && (available < MaxStackSize)) {
                            code_size++;
                            code_mask += available;
                        }
                        old_code = in_code;
                }

                // Pop a pixel off the pixel stack.

                top--;
                pixels[pi++] = pixelStack[top];
                i++;
            }

            for (i = pi; i < npix; i++) {
                pixels[i] = 0; // clear missing pixels
            }

        }


        protected void init() {
            status = STATUS_OK;
            frameCount = 0;
            frames = new ArrayList<GifFrame>();
            gct = null;
            lct = null;
        }

        protected int read() {
            int curByte = 0;
            try {
                curByte = in.read();
            } catch (IOException e) {
                status = STATUS_FORMAT_ERROR;
            }
            return curByte;
        }

        public GreenfootImage getFrame(int n) {
            GreenfootImage im = null;
            if ((n >= 0) && (n < frameCount)) {
                im = ((GifFrame) frames.get(n)).image;
            }
            return im;
        }

        public int[] getFrameSize() {
            return new int[]{width, height};
        }


        protected int readBlock() {
            blockSize = read();
            int n = 0;
            if (blockSize > 0) {
                try {
                    int count = 0;
                    while (n < blockSize) {
                        count = in.read(block, n, blockSize - n);
                        if (count == -1)
                            break;
                        n += count;
                    }
                } catch (IOException e) {
                }

                if (n < blockSize) {
                    status = STATUS_FORMAT_ERROR;
                }
            }
            return n;
        }

        protected int[] readColorTable(int ncolors) {
            int nbytes = 3 * ncolors;
            int[] tab = null;
            byte[] c = new byte[nbytes];
            int n = 0;
            try {
                n = in.read(c);
            } catch (IOException e) {
            }
            if (n < nbytes) {
                status = STATUS_FORMAT_ERROR;
            } else {
                tab = new int[256]; // max size to avoid bounds checks
                int i = 0;
                int j = 0;
                while (i < ncolors) {
                    int r = ((int) c[j++]) & 0xff;
                    int g = ((int) c[j++]) & 0xff;
                    int b = ((int) c[j++]) & 0xff;
                    tab[i++] = 0xff000000 | (r << 16) | (g << 8) | b;
                }
            }
            return tab;
        }

        protected void readGraphicControlExt() {
            read(); // block size
            int packed = read(); // packed fields
            dispose = (packed & 0x1c) >> 2; // disposal method
            if (dispose == 0) {
                dispose = 1; // elect to keep old image if discretionary
            }
            transparency = (packed & 1) != 0;
            delay = readShort() * 10; // delay in milliseconds
            transIndex = read(); // transparent color index
            read(); // block terminator
        }

        protected void readHeader() {
            String id = "";
            for (int i = 0; i < 6; i++) {
                id += (char) read();
            }
            if (!id.startsWith("GIF")) {
                status = STATUS_FORMAT_ERROR;
                return;
            }

            readLSD();
            if (gctFlag && !err()) {
                gct = readColorTable(gctSize);
                bgColor = colorFromInt(gct[bgIndex]);
            }
        }

        public int read(InputStream is) {
            init();
            if (is != null) {
                if (!(is instanceof BufferedInputStream))
                    is = new BufferedInputStream(is);
                in = (BufferedInputStream) is;
                readHeader();
                if (!err()) {
                    readContents();
                    if (frameCount < 0) {
                        status = STATUS_FORMAT_ERROR;
                    }
                }
            } else {
                status = STATUS_OPEN_ERROR;
            }
            try {
                is.close();
            } catch (IOException e) {
            }
            return status;
        }

        public int read(BufferedInputStream is) {
            init();
            if (is != null) {
                in = is;
                readHeader();
                if (!err()) {
                    readContents();
                    if (frameCount < 0) {
                        status = STATUS_FORMAT_ERROR;
                    }
                }
            } else {
                status = STATUS_OPEN_ERROR;
            }
            try {
                is.close();
            } catch (IOException e) {
            }
            return status;
        }

        public int getFrameCount() {
            return frameCount;
        }

        public GreenfootImage getImage() {
            return getFrame(0);
        }

        public int getLoopCount() {
            return loopCount;
        }

        protected void setPixels() {
            // fill in starting image contents based on last image's dispose code
            if (lastDispose > 0) {
                if (lastDispose == 3) {
                    // use image before last
                    int n = frameCount - 2;
                    if (n > 0) {
                        lastImage = getFrame(n - 1);
                    } else {
                        lastImage = null;
                    }
                }

                if (lastImage != null) {
                    image.clear();
                    image.drawImage(lastImage, 0, 0);
                    
                    // copy pixels

                    if (lastDispose == 2) {
                        // fill last image rect area with background color
                        Color c = null;
                        if (transparency) {
                            c = new Color(0, 0, 0, 0); // assume background is transparent
                        } else {
                            c = lastBgColor; // use given background color
                        }
                        for (int x = 0; x < lastRect.width; x++)
                        {
                            for (int y = 0; y < lastRect.height; y++)
                            {
                                image.setColorAt(lastRect.x + x, lastRect.y + y, c);
                            }
                        }
                    }
                }
            }

            // copy each source line to the appropriate place in the destination
            int pass = 1;
            int inc = 8;
            int iline = 0;
            for (int i = 0; i < ih; i++) {
                int line = i;
                if (interlace) {
                    if (iline >= ih) {
                        pass++;
                        switch (pass) {
                        case 2:
                            iline = 4;
                            break;
                        case 3:
                            iline = 2;
                            inc = 4;
                            break;
                        case 4:
                            iline = 1;
                            inc = 2;
                        }
                    }
                    line = iline;
                    iline += inc;
                }
                line += iy;
                if (line < height) {
                    int k = line * width;
                    int dlim = Math.min(ix + iw, width);
                    int sx = i * iw;
                    
                    for (int dx = ix; dx < dlim; dx++) {
                        int index = ((int) pixels[sx++]) & 0xff;
                        int c = act[index];
                        if (c != 0) {
                            image.setColorAt(dx, line, colorFromInt(c));
                        }
                    }
                }
            }
        }


        protected void init() {
            status = STATUS_OK;
            frameCount = 0;
            frames = new ArrayList<GifFrame>();
            gct = null;
            lct = null;
        }

        protected void readHeader() {
            String id = "";
            for (int i = 0; i < 6; i++) {
                id += (char) read();
            }
            if (!id.startsWith("GIF")) {
                status = STATUS_FORMAT_ERROR;
                return;
            }

            readLSD();
            if (gctFlag && !err()) {
                gct = readColorTable(gctSize);
                bgColor = colorFromInt(gct[bgIndex]);
            }
        }

      

        protected void readContents() {
            // read GIF file content blocks
            boolean done = false;
            while (!(done || err())) {
                int code = read();
                switch (code) {

                case 0x2C: // image separator
                    readImage();
                    break;

                case 0x21: // extension
                    code = read();
                    switch (code) {
                    case 0xf9: // graphics control extension
                        readGraphicControlExt();
                        break;

                    case 0xff: // application extension
                        readBlock();
                        String app = "";
                        for (int i = 0; i < 11; i++) {
                            app += (char) block[i];
                        }
                        if (app.equals("NETSCAPE2.0")) {
                            readNetscapeExt();
                        } else
                            skip(); // don't care
                        break;

                    default: // uninteresting extension
                        skip();
                    }
                    break;

                case 0x3b: // terminator
                    done = true;
                    break;

                case 0x00: // bad byte, but keep going and see what happens
                    break;

                default:
                    status = STATUS_FORMAT_ERROR;
                }
            }
        }

        protected void readLSD() {

            // logical screen size
            width = readShort();
            height = readShort();

            // packed fields
            int packed = read();
            gctFlag = (packed & 0x80) != 0; // 1 : global color table flag
            // 2-4 : color resolution
            // 5 : gct sort flag
            gctSize = 2 << (packed & 7); // 6-8 : gct size

            bgIndex = read(); // background color index
            pixelAspect = read(); // pixel aspect ratio
        }

        protected void readNetscapeExt() {
            do {
                readBlock();
                if (block[0] == 1) {
                    // loop count sub-block
                    int b1 = ((int) block[1]) & 0xff;
                    int b2 = ((int) block[2]) & 0xff;
                    loopCount = (b2 << 8) | b1;
                }
            } while ((blockSize > 0) && !err());
        }

        protected int readShort() {
            // read 16-bit value, LSB first
            return read() | (read() << 8);
        }

        protected boolean err() {
            return status != STATUS_OK;
        }

        protected void resetFrame() {
            lastDispose = dispose;
            lastRect = new Rectangle(ix, iy, iw, ih);
            lastImage = image;
            lastBgColor = bgColor;
            int dispose = 0;
            boolean transparency = false;
            int delay = 0;
            lct = null;
        }

        protected void skip() {
            do {
                readBlock();
            } while ((blockSize > 0) && !err());
        }



    }

}