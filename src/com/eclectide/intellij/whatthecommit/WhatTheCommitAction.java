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
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.util.net.IOExceptionDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author darekkay
 */
public class WhatTheCommitAction extends AnAction implements DumbAware {

    private static final String URL = "http://whatthecommit.com/index.txt";
    private static final int TIMEOUT_SECONDS = 5;

    public void actionPerformed(@NotNull AnActionEvent e) {
        final CommitMessageI checkinPanel = getCheckinPanel(e);
        if (checkinPanel == null)
            return;

        String commitMessage = loadCommitMessage(URL);
        if (!commitMessage.isEmpty()) {
            checkinPanel.setCommitMessage(commitMessage);
        }
    }

    private String readUrlContent(String urlString) {
        HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();

        HttpURLConnection connection = null;

        try {
            connection = httpConfigurable.openHttpConnection(urlString);

            String text = StreamUtil.readText(connection.getInputStream(), "UTF-8");
            return text.trim();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public String loadCommitMessage(final String url) {
        final FutureTask<String> downloadTask = new FutureTask<String>(new Callable<String>() {
            public String call() {		
				try {
				  final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
				  httpConfigurable.prepareURL(url);
				} catch (IOException ioe) {
				  IOExceptionDialog.showErrorDialog("Error", String.format("Unable to connect to \"%s\". Make sure your proxy settings are correct.", url));
				  return null;
				}

				return readUrlContent(url);
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
        return VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(e.getDataContext());
    }
}
