/*
 * Copyright (c) 2015, Gluon
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *     * Neither the name of Gluon, any associated website, nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL GLUON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.impl.charm.down.plugins.android.scan.zxing.res.layout;

import android.content.Context;
import android.graphics.Color;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.gluonhq.impl.charm.down.plugins.android.scan.zxing.ViewfinderView;

public class CaptureActivityView extends FrameLayout {

    private final SurfaceView previewView;
    private final ViewfinderView viewfinderView;
    private final TextView statusView;

    public CaptureActivityView(Context context) {
        super(context);

        previewView = new SurfaceView(context);
        previewView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        viewfinderView = new ViewfinderView(context);
        viewfinderView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        statusView = new TextView(context);
        statusView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0x01 | 0x50));
        statusView.setBackgroundColor(Color.TRANSPARENT);
        statusView.setText("Place a barcode inside the viewfinder rectangle to scan it.");
        statusView.setTextColor(Color.WHITE);

        addView(previewView, 0);
        addView(viewfinderView, 1);
        addView(statusView, 2);
    }

    public SurfaceView getPreviewView() {
        return previewView;
    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public TextView getStatusView() {
        return statusView;
    }
}
