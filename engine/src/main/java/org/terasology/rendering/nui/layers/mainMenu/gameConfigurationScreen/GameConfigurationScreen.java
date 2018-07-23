/*
 * Copyright 2018 MovingBlocks
 *
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
 */
package org.terasology.rendering.nui.layers.mainMenu.gameConfigurationScreen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.assets.ResourceUrn;
import org.terasology.rendering.nui.CoreScreenLayer;
import org.terasology.rendering.nui.WidgetUtil;
import org.terasology.rendering.nui.layers.mainMenu.savedGames.GameInfo;

/**
 * This screen allows the user configure saved game.
 * Each modules can provide shared own variables, and those values can be changed by user.
 */
public class GameConfigurationScreen extends CoreScreenLayer {
    public static final ResourceUrn ASSET_URI = new ResourceUrn("engine:gameConfigurationScreen");
    private static final Logger logger = LoggerFactory.getLogger(GameConfigurationScreen.class);

    private GameInfo gameInfo;

    @Override
    public void initialise() {

        WidgetUtil.trySubscribe(this, "close", button -> triggerBackAnimation());
    }

    public GameInfo getGameInfo() {
        return gameInfo;
    }

    public GameConfigurationScreen setGameInfo(GameInfo gameInfo) {
        this.gameInfo = gameInfo;
        return this;
    }
}
