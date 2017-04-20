//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//

package com.wire.bots.broadcast.model;

import java.util.ArrayList;

public class Config extends com.wire.bots.sdk.Configuration {
    private String database = "crypto/broadcast.db";
    private String admin;
    private ArrayList<String> whitelist;
    private String onNewSubscriberLabel;
    private long fallback;
    private String channelName;
    private boolean like = true;
    private long expiration;
    private String appSecret;
    private String host;

    public String getDatabase() {
        return database;
    }

    public String getAdmin() {
        return admin;
    }

    public ArrayList<String> getWhitelist() {
        return whitelist;
    }

    public String getOnNewSubscriberLabel() {
        return onNewSubscriberLabel;
    }

    public long getFallback() {
        return fallback;
    }

    public String getChannelName() {
        return channelName;
    }

    public boolean isLike() {
        return like;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public String getHost() { return host; }
}
