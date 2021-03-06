package defectstudy.callable;

/*-
 * #%L
 * tuna
 * %%
 * Copyright (C) 2018 Maxime Cordy, Matthieu Jimenez, University of Luxembourg and Namur
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import modelling.util.Pair;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

public class CountLine implements Callable<Pair<String, Integer>> {

    private final String pathToRemove;
    private final String path;

    public CountLine(String path, String pathToRemove) {
        this.path = path;
        this.pathToRemove = pathToRemove;
    }

    @Override
    public Pair<String, Integer> call() throws Exception {
        return new Pair<>(path.replace(pathToRemove, ""), countLines(path));
    }


    public static int countLines(String filename) throws IOException {
        int count = 0;
        boolean empty = true;
        FileInputStream fis = null;
        InputStream is = null;
        try {
            fis = new FileInputStream(filename);
            is = new BufferedInputStream(fis);
            byte[] c = new byte[1024];
            int readChars = 0;
            boolean isLine = false;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        isLine = false;
                        ++count;
                    } else if (!isLine && c[i] != '\n' && c[i] != '\r') {   //Case to handle line count where no New Line character present at EOF
                        isLine = true;
                    }
                }
            }
            if (isLine) {
                ++count;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                is.close();
            }
            if (fis != null) {
                fis.close();
            }
        }
        return (count == 0 && !empty) ? 1 : count;
    }
}
