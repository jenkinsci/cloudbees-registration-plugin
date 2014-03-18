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

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

/**
 * Handles the response from "
 */
class ApplicationsStatusHandler extends AsyncCompletionHandler<Map<String, String>> {
    static final String API_METHOD = "application.list";

    @Override
    public Map<String, String> onCompleted(Response response) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        Map<String, String> result = new TreeMap<String, String>();
        InputStream inputStream = null;
        XMLEventReader reader = null;
        try {
            inputStream = response.getResponseBodyAsStream();
            reader = factory.createXMLEventReader(inputStream);
            Stack<String> paths = new Stack<String>();
            String path = "";
            String id = null;
            String status = null;
            String errorCode = null;
            String errorMessage = null;
            boolean inApplicationInfo = false;
            boolean inError = false;
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                switch (event.getEventType()) {
                    case XMLEvent.START_ELEMENT:
                        String localPart = event.asStartElement().getName().getLocalPart();
                        if (inApplicationInfo && "id".equals(localPart)) {
                            id = reader.getElementText();
                        } else if (inApplicationInfo && "status".equals(localPart)) {
                            status = reader.getElementText();
                        } else if (inError && "errorCode".equals(localPart)) {
                            errorCode = reader.getElementText();
                        } else if (inError && "message".equals(localPart)) {
                            errorMessage = reader.getElementText();
                        } else {
                            paths.push(path);
                            path = path + '/' + localPart;
                            inApplicationInfo =
                                    "/ApplicationListResponse/applications/ApplicationInfo"
                                            .equals(path);
                            if (inApplicationInfo) {
                                id = null;
                                status = null;
                            }
                            inError = !inApplicationInfo && "/error".equals(path);
                            if (inError) {
                                errorCode = null;
                                errorMessage = null;
                            }
                        }
                        break;
                    case XMLEvent.END_ELEMENT:
                        if (inApplicationInfo) {
                            if (id != null && status != null) {
                                result.put(id, status);
                            }
                        }
                        if (inError) {
                            throw new CloudBeesException(errorCode, errorMessage);
                        }
                        path = paths.pop();
                        inApplicationInfo =
                                "/ApplicationListResponse/applications/ApplicationInfo"
                                        .equals(path);
                        inError = !inApplicationInfo && "/error".equals(path);
                        break;
                    default:
                        // ignore
                        break;
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    // ignore
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return result;
    }
}
