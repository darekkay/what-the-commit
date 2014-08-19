/*
 * Copyright 2014 Darek Kay <darekkay@eclectide.com>
 *
 * MIT license
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE
 * OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.eclectide.intellij.whatthecommit;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.ui.Refreshable;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author darekkay
 */
public class WhatTheCommitAction extends AnAction {

    private static final String URL = "http://whatthecommit.com/index.txt";
    private static final int TIMEOUT_SECONDS = 5;

    public void actionPerformed(AnActionEvent e) {
        final CommitMessageI checkinPanel = getCheckinPanel(e);
        if (checkinPanel == null)
            return;

        String commitMessage = loadCommitMessage(URL);
        if (!commitMessage.isEmpty()) {
            checkinPanel.setCommitMessage(commitMessage);
        }
    }

    public String loadCommitMessage(final String url) {
        final FutureTask<String> downloadTask = new FutureTask<String>(new Callable<String>() {
            public String call() {
                final HttpClient client = new HttpClient();
                final GetMethod getMethod = new GetMethod(url);
                try {
                    final int statusCode = client.executeMethod(getMethod);
                    if (statusCode != HttpStatus.SC_OK)
                        throw new RuntimeException("Connection error (HTTP status = " + statusCode + ")");
                    return getMethod.getResponseBodyAsString();
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        });

        ApplicationManager.getApplication().executeOnPooledThread(downloadTask);

        try {
            return downloadTask.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // ignore
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (!downloadTask.isDone()) {
            downloadTask.cancel(true);
            throw new RuntimeException("Connection timed out");
        }

        return "";
    }

    @Nullable
    private static CommitMessageI getCheckinPanel(@Nullable AnActionEvent e) {
        if (e == null) {
            return null;
        }
        Refreshable data = Refreshable.PANEL_KEY.getData(e.getDataContext());
        if (data instanceof CommitMessageI) {
            return (CommitMessageI) data;
        }
        CommitMessageI commitMessageI = VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(e.getDataContext());
        if (commitMessageI != null) {
            return commitMessageI;
        }
        return null;
    }
}
