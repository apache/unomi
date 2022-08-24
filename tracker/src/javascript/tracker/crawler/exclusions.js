/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {Provider} from './provider';

export class Exclusions extends Provider
{
	constructor()
	{
		super();

		this.data = ["Safari.[\\d\\.]*","Firefox.[\\d\\.]*"," Chrome.[\\d\\.]*","Chromium.[\\d\\.]*","MSIE.[\\d\\.]","Opera\\\/[\\d\\.]*","Mozilla.[\\d\\.]*","AppleWebKit.[\\d\\.]*","Trident.[\\d\\.]*","Windows NT.[\\d\\.]*","Android [\\d\\.]*","Macintosh.","Ubuntu","Linux","[ ]Intel","Mac OS X [\\d_]*","(like )?Gecko(.[\\d\\.]*)?","KHTML,","CriOS.[\\d\\.]*","CPU iPhone OS ([0-9_])* like Mac OS X","CPU OS ([0-9_])* like Mac OS X","iPod","compatible","x86_..","i686","x64","X11","rv:[\\d\\.]*","Version.[\\d\\.]*","WOW64","Win64","Dalvik.[\\d\\.]*"," \\.NET CLR [\\d\\.]*","Presto.[\\d\\.]*","Media Center PC","BlackBerry","Build","Opera Mini\\\/\\d{1,2}\\.\\d{1,2}\\.[\\d\\.]*\\\/\\d{1,2}\\.","Opera"," \\.NET[\\d\\.]*","cubot","; M bot","; CRONO","; B bot","; IDbot","; ID bot","; POWER BOT",";"];
	}
}
