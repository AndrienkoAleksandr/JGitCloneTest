/*
 * Copyright (C) 2006, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.che;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lfs.CleanFilter;
import org.eclipse.jgit.lfs.SmudgeFilter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.eclipse.jgit.util.CachedAuthenticator;

import java.io.File;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.text.MessageFormat;

import static jdk.nashorn.internal.objects.Global.exit;

public class GitCloneApplication {

    /* Correct behaviour: after clone default branch "New". See more https://github.com/AndrienkoAleksandr/JGitTest2/branches. */
    //private static final String GIT_URL = "https://github.com/AndrienkoAleksandr/JGitTest2.git";

    /* Incorrect behaviour, because default branch after clone should be "develop". See more: https://github.com/idexmai/testRepo-2/branches. */
    private static final String GIT_URL = "https://github.com/idexmai/testRepo-2";

    private void init() throws MalformedURLException {
        HttpTransport.setConnectionFactory(new HttpClientConnectionFactory());
        CleanFilter.register();
        SmudgeFilter.register();

        configureHttpProxy();
    }

    public static void main(String[] args) {
        GitCloneApplication app = new GitCloneApplication();

        Repository db = null;
        try {
            app.init();

            final URIish uri = new URIish(GIT_URL);
            String repoFolderName = uri.getHumanishName();
            File localNameF = Files.createTempDirectory(repoFolderName).toFile();
            String branch = Constants.HEAD;

            CloneCommand command = Git.cloneRepository();
            command.setURI(GIT_URL)
                   .setRemote("origin")
                   .setBare(false)
                   .setNoCheckout(false)
                   .setBranch(branch)
                   .setCloneSubmodules(false)
                   .setGitDir(null)
                   .setDirectory(localNameF);

            PrintWriter outw = new PrintWriter(System.out);
            command.setProgressMonitor(new TextProgressMonitor(outw));
            outw.println("Cloning repository " + repoFolderName + " to the target path : " + localNameF.getAbsolutePath());
            outw.flush();

            db = command.call().getRepository();
            outw.println("Working branch after clone : " + db.getBranch());
            outw.flush();

            if (db.resolve(Constants.HEAD) == null) outw.println("Clone empty repository");
        } catch (Exception e) {
            exit(1, e.getMessage());
        } finally {
            if (db != null) db.close();
        }
    }

    /**
     * Configure the JRE's standard HTTP based on <code>http_proxy</code>.
     * <p>
     * The popular libcurl library honors the <code>http_proxy</code>,
     * <code>https_proxy</code> environment variables as a means of specifying
     * an HTTP/S proxy for requests made behind a firewall. This is not natively
     * recognized by the JRE, so this method can be used by command line
     * utilities to configure the JRE before the first request is sent. The
     * information found in the environment variables is copied to the
     * associated system properties. This is not done when the system properties
     * are already set. The default way of telling java programs about proxies
     * (the system properties) takes precedence over environment variables.
     *
     * @throws MalformedURLException
     *             the value in <code>http_proxy</code> or
     *             <code>https_proxy</code> is unsupportable.
     */
    static void configureHttpProxy() throws MalformedURLException {
        for (String protocol : new String[] { "http", "https" }) { //$NON-NLS-1$ //$NON-NLS-2$
            if (System.getProperty(protocol + ".proxyHost") != null) { //$NON-NLS-1$
                continue;
            }
            String s = System.getenv(protocol + "_proxy"); //$NON-NLS-1$
            if (s == null && protocol.equals("https")) { //$NON-NLS-1$
                s = System.getenv("HTTPS_PROXY"); //$NON-NLS-1$
            }
            if (s == null || s.equals("")) { //$NON-NLS-1$
                continue;
            }

            final URL u = new URL(
                    (s.indexOf("://") == -1) ? protocol + "://" + s : s); //$NON-NLS-1$ //$NON-NLS-2$
            if (!u.getProtocol().startsWith("http")) //$NON-NLS-1$
                throw new MalformedURLException(MessageFormat.format("Http proxy supported only", s));

            final String proxyHost = u.getHost();
            final int proxyPort = u.getPort();

            System.setProperty(protocol + ".proxyHost", proxyHost); //$NON-NLS-1$
            if (proxyPort > 0)
                System.setProperty(protocol + ".proxyPort", //$NON-NLS-1$
                                   String.valueOf(proxyPort));

            final String userpass = u.getUserInfo();
            if (userpass != null && userpass.contains(":")) { //$NON-NLS-1$
                final int c = userpass.indexOf(':');
                final String user = userpass.substring(0, c);
                final String pass = userpass.substring(c + 1);
                CachedAuthenticator.add(
                        new CachedAuthenticator.CachedAuthentication(proxyHost,
                                                                     proxyPort, user, pass));
            }
        }
    }
}
