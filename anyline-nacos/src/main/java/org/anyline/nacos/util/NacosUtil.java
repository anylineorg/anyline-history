package org.anyline.nacos.util;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.anyline.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

@Component
public class NacosUtil {
	private static Logger log = LoggerFactory.getLogger(NacosUtil.class);
	private NacosConfig config = null;

	private static Hashtable<String, NacosUtil> instances = new Hashtable<String, NacosUtil>();
	
	static{
		NacosUtil util = NacosUtil.getInstance();
		if(util.config.AUTO_SCAN){
			String packages = util.config.SCAN_PACKAGE;
			if(BasicUtil.isNotEmpty(packages)){
				String[] pks = packages.split(",");
				if(null != pks) {
					for (String pk:pks) {
						List<Class<?>> list = ClassUtil.list(pk, true, AnylineConfig.class, ConfigTable.class);
						for(Class<?> clazz:list){
							@SuppressWarnings("unchecked")
							Class<AnylineConfig> configClass = (Class<AnylineConfig>)clazz;
							String configName = (String)BeanUtil.getFieldValue(clazz, "CONFIG_NAME");
							try {
								util.config(null, configName, configClass);
							} catch (NacosException e) {
								log.warn("[nacos config][result:false][config:{}]", configName);
							}
						}
					}
				}
			}
			String cls = util.config.SCAN_CLASS;
			if(BasicUtil.isNotEmpty(cls)){
				String[] clas  = cls.split(",");
				for(String c:clas){
					try {
						Class clazz = Class.forName(c);
						@SuppressWarnings("unchecked")
						Class<AnylineConfig> configClass = (Class<AnylineConfig>)clazz;
						String configName = (String)BeanUtil.getFieldValue(clazz, "CONFIG_NAME");
						util.config(null, configName, configClass);
					}catch (Exception e){
						e.printStackTrace();
					}

				}
			}
		}
	}

	public static NacosUtil getInstance(){
		return getInstance("default");
	}
	public static NacosUtil getInstance(String key){
		if(BasicUtil.isEmpty(key)){
			key = "default";
		}
		NacosUtil util = instances.get(key);
		if(null == util){
			util = new NacosUtil();
			NacosConfig config = NacosConfig.getInstance(key);
			util.config = config;
			instances.put(key, util);
		}
		return util;
	}
	public String config(String group, String data, final Class<? extends AnylineConfig> T) throws NacosException{
		if(BasicUtil.isEmpty(group)){
			group = config.GROUP;
		}
		log.warn("[nacos config][group:{}][data:{}][class:{}]", group, data, T.getName());
		final String gp = group;
		final String dt = data;
		Listener listener = new Listener() {
			@Override
			public void receiveConfigInfo(String content) {
				log.warn("[nacos reload config][group:{}][data:{}][class:{}]", gp, dt, T.getName());
				parse(T, content);
			}
			@Override
			public Executor getExecutor() {
				return null;
			}
		};
		String config = config(group,data,listener);
		parse(T, config);
		return config;
	}

	/**
	 * ?????????????????? ??? ??????????????????
	 * @param group group
	 * @param data data
	 * @param listener listent
	 * @return String
	 * @throws NacosException NacosException
	 */
	public String config(String group, String data, Listener listener) throws NacosException{
		if(BasicUtil.isEmpty(group)){
			group = config.GROUP;
		}
		log.warn("[nacos config][group:{}][data:{}][listener:{}]", group, data, listener);
		Properties properties = new Properties();
		properties.put(PropertyKeyConst.NAMESPACE, config.NAMESPACE);
		properties.put(PropertyKeyConst.SERVER_ADDR, config.ADDRESS+":"+config.PORT);
		ConfigService configService = NacosFactory.createConfigService(properties);
		String content = configService.getConfig(data, group, config.TIMEOUT);
		if(null != listener) {
			configService.addListener(data, group, listener);
		}
		return content;
	}
	public String config(String data){
		try{
			Listener listener = null;
			return config(null, data, listener);
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}
	public String config(String group, String data){
		try{
			Listener listener = null;
			return config(group, data, listener);
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	public static void parse(Class<? extends AnylineConfig> T, String content) {
		if(BasicUtil.isNotEmpty(content)){
			log.warn("[nacos config][pull fail][config class:{}]",T.getName());
			return;
		}
		try{
		    Class<?> clazz = Class.forName(T.getName());
		    Method method = clazz.getMethod("parse", String.class);
		    method.invoke(null, content);
		}catch(Exception e){
			
		}
	}
}
