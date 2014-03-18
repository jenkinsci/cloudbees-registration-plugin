/*
 * The MIT License
 *
 * Copyright 2014 CloudBees.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.plugins.registration.run;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
* Created by IntelliJ IDEA.
* User: stephenc
* Date: 08/12/2011
* Time: 11:19
* To change this template use File | Settings | File Templates.
*/
public class SignatureAlgorithmV1Impl implements SignatureAlgorithm {

    public String getVersion() {
        return "1";
    }

    public String getSignature(Map<String, String> params, String secret) {
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<String, String>(params).entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            data.append(key);
            if (value != null) {
                data.append(value);
            }
        }
        data.append(secret);
        return md5(data.toString());
    }

    public static String md5(String message) {
        try {
            MessageDigest instance = MessageDigest.getInstance("MD5");
            byte[] digest = instance.digest(message.getBytes("CP1252"));
            String result = new BigInteger(1, digest).toString(16);
            if (result.length() < 32) {
                char[] padded = new char[32];
                char[] raw = result.toCharArray();
                Arrays.fill(padded, 0, 32 - raw.length, '0');
                System.arraycopy(raw, 0, padded, 32 - raw.length, raw.length);
                result = new String(padded);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM is supposed to have the MD5 digest algorithm", e);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("JVM is supposed to have the CP1252 charset", e);
        }
    }

}
