/* 
 * Copyright 2006-2015 www.anyline.org
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
 *
 *          
 */
package org.anyline.entity;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;

import org.anyline.util.BasicUtil;
import org.anyline.util.BeanUtil;
import org.anyline.util.ConfigTable;
import org.anyline.util.DateUtil;
import org.anyline.util.JSONDateFormatProcessor;
import org.anyline.util.NumberUtil;
import org.apache.log4j.Logger;

public class DataRow extends HashMap<String, Object> implements Serializable{
	private static final long serialVersionUID = -2098827041540802313L;
	private static final Logger log = Logger.getLogger(DataRow.class);

	public static enum KEY_CASE{
		DEFAULT				{public String getCode(){return "DEFAULT";} 	public String getName(){return "默认";}},
		UPPER				{public String getCode(){return "UPPER";} 	public String getName(){return "强制大写";}},
		LOWER				{public String getCode(){return "LOWER";} 	public String getName(){return "强制小写";}};
		public abstract String getName();
		public abstract String getCode();
	}
	public static String PARENT 			= "PARENT"						; //上级数据
	public static String ALL_PARENT 		= "ALL_PARENT"					; //所有上级数据
	public static String CHILDREN 			= "CHILDREN"					; //子数据
	public static String PRIMARY_KEY		= ConfigTable.getString("DEFAULT_PRIMARY_KEY","id");
	public static String ITEMS				= "ITEMS"						;
	private DataSet container				= null							; //包含当前对象的容器

	private List<String> primaryKeys 		= new ArrayList<String>()		; //主键
	private List<String> updateColumns 		= new ArrayList<String>()		;
	private String datalink					= null							;
	private String dataSource				= null 							; //数据源(表|视图|XML定义SQL)
	private String schema					= null							;
	private String table					= null							;
	private Map<String, Object> queryParams	= new HashMap<String,Object>()	; //查询条件
	private Object clientTrace				= null							; //客户端数据
	private long createTime 				= 0								; //创建时间
	private long expires 					= -1							; //过期时间(毫秒) 从创建时刻计时expires毫秒后过期
	protected Boolean isNew 				= false							; //强制新建(适应hibernate主键策略)
	protected boolean isFromCache 			= false							; //是否来自缓存

	private boolean updateNullColumn 		= ConfigTable.getBoolean("IS_UPDATE_NULL_COLUMN", true);
	private boolean updateEmptyColumn 		= ConfigTable.getBoolean("IS_UPDATE_EMPTY_COLUMN", true);
	
	private KEY_CASE keyCase = KEY_CASE.DEFAULT;

	public DataRow(){
		String pk = key(PRIMARY_KEY);
		if(null != pk){
			primaryKeys.add(PRIMARY_KEY);
		}
		createTime = System.currentTimeMillis();
	}
	public DataRow(String table){
		this();
		this.setTable(table);
	}
	public DataRow(Map<String,Object> map){
		this();
		for(Iterator<String> itr=map.keySet().iterator(); itr.hasNext();){
			String key = itr.next();
			Object value = map.get(key);
			put(key(key), value);
		}
	}
	/**
	 * 解析实体类对象
	 * @param obj
	 * @param keys 列名:obj属性名 "ID:memberId"
	 * @return
	 */
	public static DataRow parse(Object obj, String ... keys){
		Map<String,String> map = new HashMap<String,String>();
		if(null != keys){
			for(String key:keys){
				String tmp[] = key.split(":");
				if(null != tmp && tmp.length>1){
					map.put(keyCase(tmp[1].trim()), keyCase(tmp[0].trim()));
				}
			}
		}
		DataRow row = new DataRow();
		if(null != obj){
			if(obj instanceof JSONObject){
				row = parseJson((JSONObject)obj);
			}else if(obj instanceof DataRow){
				row = (DataRow)obj;
			}else if(obj instanceof Map){
				Map mp = (Map)obj;
				List<String> ks = BeanUtil.getMapKeys(mp);
				for(String k:ks){
					row.put(k, mp.get(k));
				}
			}else{
				List<String> fields = BeanUtil.getFieldsName(obj.getClass());
				for(String field : fields){
					String col = map.get(keyCase(field));
					if(null == col){
						col = field;
					}
					row.put(col, BeanUtil.getFieldValue(obj, field));
				}
			}
		}
		return row;
	}
	/**
	 * 解析json结构字符
	 * @param json
	 * @return
	 */
	public static DataRow parseJson(String json){
		if(null != json){
			try{
				return parseJson(JSONObject.fromObject(json));
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		return null;
	}
	/**
	 * 解析JSONObject
	 * @param json
	 * @return
	 */
	public static DataRow parseJson(JSONObject json){
		DataRow row = new DataRow();
		if(null == json){
			return row;
		}
		Iterator<?>  itr = json.keys();
		while(itr.hasNext()){
			String key = itr.next().toString();
			Object val = json.get(key);
			if(null != val){
				if(val instanceof JSONObject){
					row.put(key, parseJson((JSONObject)val));
				}else if(val instanceof JSONArray){
					row.put(key, parseJson((JSONArray)val));
				}else if(val instanceof JSONNull){
					row.put(key, null);
				}else if(val instanceof String){
					if("null".equalsIgnoreCase((String)val)){
						row.put(key, null);
					}else{
						row.put(key, val);
					}
				}else{
					row.put(key, val);
				}
			}
		}
		return row;
	}
	/**
	 * 解析JSON集合
	 * @param array
	 * @return
	 */
	public static List<Object> parseJson(JSONArray array){
		List<Object> list = new ArrayList<Object>();
		int size = array.size();
		for(int i=0; i<size; i++){
			Object val = array.get(i);
			if(null != val){
				if(val instanceof JSONObject){
					list.add(parseJson((JSONObject)val));
				}else if(val instanceof JSONArray){
					list.add(parseJson((JSONArray)val));
				}else{
					list.add(val);
				}
			}
		}
		return list;
	}
	/**
	 * 创建时间
	 * @return
	 */
	public long getCreateTime(){
		return createTime;
	}
	/**
	 * 过期时间
	 * @return
	 */
	public long getExpires() {
		return expires;
	}
	/**
	 * 设置过期时间
	 * @param millisecond
	 * @return
	 */
	public DataRow setExpires(long millisecond) {
		this.expires = millisecond;
		return this;
	}
	public DataRow setExpires(int millisecond) {
		this.expires = millisecond;
		return this;
	}
	/**
	 * 合并数据
	 * @param row 
	 * @param over key相同时是否覆盖原数据
	 * @return
	 */
	public DataRow merge(DataRow row, boolean over){
		List<String> keys = row.keys();
		for(String key : keys){
			if(over || null != this.get(key)){
				this.put(key, row.get(key));
			}
		}
		return this;
	}
	public DataRow merge(DataRow row){
		return merge(row, false);
	}
	/**
	 * 是否是新数据
	 * @return
	 */
	public Boolean isNew() {
		String pk = getPrimaryKey();
		String pv = getString(pk);
		return (null == pv ||(null == isNew)|| isNew || BasicUtil.isEmpty(pv));
	}
	/**
	 * 是否来自缓存
	 * @return
	 */
	public boolean isFromCache(){
		return isFromCache;
	}
	/**
	 * 设置是否来自缓存
	 * @param bol
	 * @return
	 */
	public DataRow setIsFromCache(boolean bol){
		this.isFromCache = bol;
		return this;
	}
	public String getCd(){
		return getString("cd");
	}
	public String getId(){
		return getString("id");
	}
	public String getCode(){
		return getString("code");
	}
	public String getNm(){
		return getString("nm");
	}
	public String getName(){
		return getString("name");
	}
	public String getTitle(){
		return getString("title");
	}
	/**
	 * 默认子集
	 * @return
	 */
	public DataSet getItems(){
		Object items = get(ITEMS);
		if(items instanceof DataSet){
			return (DataSet)items;
		}
		return null;
	}
	public DataRow putItems(Object obj){
		put(ITEMS,obj);
		return this;
	}
	/**
	 * key转换成小写
	 * @param keys
	 * @return
	 */
	public DataRow toLowerKey(String ... keys){
		if(null != keys && keys.length>0){
			for(String key:keys){
				Object value = get(key);
				remove(key(key));
				put(KEY_CASE.LOWER, key, value);
			}
		}else{
			for(String key:keys()){
				Object value = get(key(key));
				remove(key(key));
				put(KEY_CASE.LOWER, key, value);
			}
		}
		this.keyCase = KEY_CASE.LOWER;
		return this;
	}
	/**
	 * key转换成大写
	 * @param keys
	 * @return
	 */
	public DataRow toUpperKey(String ... keys){
		if(null != keys && keys.length>0){
			for(String key:keys){
				Object value = get(key);
				remove(key(key));
				put(KEY_CASE.UPPER, key, value);
			}
		}else{
			for(String key:keys()){
				Object value = get(key);
				remove(key(key));
				put(KEY_CASE.UPPER,key, value);
			}
		}
		this.keyCase = KEY_CASE.UPPER;
		return this;
	}
	/**
	 * 数字格式化
	 * @param format
	 * @param cols
	 * @return
	 */
	public DataRow formatNumber(String format, String ... cols){
		if(null == cols || BasicUtil.isEmpty(format)){
			return this;
		}
		for(String col:cols){
			String value = getString(col);
			if(null != value){
				value = NumberUtil.format(value, format);
				put(col, value);
			}
		}
		return this;
	}
	/**
	 * 日期格式化
	 * @param format
	 * @param cols
	 * @return
	 */
	public DataRow formatDate(String format, String ... cols){
		if(null == cols || BasicUtil.isEmpty(format)){
			return this;
		}
		for(String col:cols){
			String value = getString(col);
			if(null != value){
				value = DateUtil.format(value, format);
				put(col, value);
			}
		}
		return this;
	}
	/**
	 * 指定列是否为空
	 * @param key
	 * @return
	 */
	public boolean isNull(String key){
		Object obj = get(key);
		return obj == null;
	}
	public boolean isNotNull(String key){
		return ! isNull(key);
	}
	public boolean isEmpty(String key){
		Object obj = get(key);
		return BasicUtil.isEmpty(obj); 
	}
	public boolean isNotEmpty(String key){
		return !isEmpty(key);
	}
	
	/**
	 * 添加主键
	 * @param applyContainer 是否应用到上级容器 默认false
	 * @param primary
	 */
	public DataRow addPrimaryKey(boolean applyContainer, String ... pks){
		if(null != pks){
			List<String> list = new ArrayList<String>();
			for(String pk:pks){
				list.add(pk);
			}
			return addPrimaryKey(applyContainer, list);
		}
		return this;
	}
	public DataRow addPrimaryKey(String ... pks){
		return addPrimaryKey(false, pks);
	}
	public DataRow addPrimaryKey(boolean applyContainer, Collection<String> pks){
		if(BasicUtil.isEmpty(pks)){
			return this;
		}
		
		/*没有处于容器中时,设置自身主键*/
		if(null == this.primaryKeys){
			this.primaryKeys = new ArrayList<String>();
		}
		for(String item:pks){
			if(BasicUtil.isEmpty(item)){
				continue;
			}
			item = key(item);
			if(!this.primaryKeys.contains(item)){
				this.primaryKeys.add(item);
			}
		}
		/*设置容器主键*/
		if(hasContainer() && applyContainer){
			getContainer().setPrimaryKey(false, primaryKeys);
		}
		return this;
	}
	
	public DataRow setPrimaryKey(boolean applyContainer, String ... pks){
		if(null != pks){
			List<String> list = new ArrayList<String>();
			for(String pk:pks){
				list.add(pk);
			}
			return setPrimaryKey(applyContainer, list);
		}
		return this;
	}
	public DataRow setPrimaryKey(String ... pks){
		return setPrimaryKey(false, pks);
	}
	public DataRow setPrimaryKey(boolean applyContainer, Collection<String> pks){
		if(BasicUtil.isEmpty(pks)){
			return this;
		}
		/*设置容器主键*/
		if(hasContainer() && applyContainer){
			getContainer().setPrimaryKey(pks);
		}
		
		if(null == this.primaryKeys){
			this.primaryKeys = new ArrayList<String>();
		}else{
			this.primaryKeys.clear();
		}
		return addPrimaryKey(applyContainer, pks);
	}
	public DataRow setPrimaryKey(Collection<String> pks){
		return setPrimaryKey(false, pks);
	}
	/**
	 * 读取主键
	 * 主键为空时且容器有主键时,读取容器主键,否则返回默认主键
	 * @return
	 */
	public List<String> getPrimaryKeys(){
		/*有主键直接返回*/
		if(hasSelfPrimaryKeys()){
			return primaryKeys;
		}
		
		/*处于容器中并且容器有主键,返回容器主键*/
		if(hasContainer() && getContainer().hasPrimaryKeys()){
			return getContainer().getPrimaryKeys();
		}
		
		/*本身与容器都没有主键 返回默认主键*/
		List<String> defaultPrimary = new ArrayList<String>();
		String configKey = ConfigTable.getString("DEFAULT_PRIMARY_KEY");
		if(null != configKey && !configKey.trim().equals("")){
			defaultPrimary.add(configKey);	
		}

		return defaultPrimary;
	}
	public String getPrimaryKey(){
		List<String> keys = getPrimaryKeys();
		if(null != keys && keys.size()>0){
			return keys.get(0); 
		}
		return null;
	}
	/**
	 * 主键值
	 * @return
	 */
	public List<Object> getPrimaryValues(){
		List<Object> values = new ArrayList<Object>();
		List<String> keys = getPrimaryKeys();
		if(null != keys){
			for(String key:keys){
				values.add(get(key));
			}
		}
		return values;
	}
	public Object getPrimaryValue(){
		String key = getPrimaryKey();
		if(null != key){
			return get(key);
		}
		return null;
	}
	/**
	 * 是否有主键
	 * @return
	 */
	public boolean hasPrimaryKeys(){
		if(hasSelfPrimaryKeys()){
			return true;
		}
		if(null != getContainer()){
			return getContainer().hasPrimaryKeys();
		}
		if(keys().contains(ConfigTable.getString("DEFAULT_PRIMARY_KEY"))){
			return true;
		}
		return false;
	}
	/**
	 * 自身是否有主键
	 * @return
	 */
	public boolean hasSelfPrimaryKeys(){
		if(null != primaryKeys && primaryKeys.size()>0){
			return true;
		}else{
			return false;
		}
	}
	
	/**
	 * 读取数据源
	 * 数据源为空时,读取容器数据源
	 * @return
	 */
	public String getDataSource() {
		String ds = table;
		if(BasicUtil.isNotEmpty(ds) && BasicUtil.isNotEmpty(schema)){
			ds = schema + "." + ds;
		}
		if(BasicUtil.isEmpty(ds)){
			ds = dataSource;
		}
		if(null == ds && null != getContainer()){
			ds = getContainer().getDataSource();
		}
		
		return ds;
	}
	public String getDataLink() {
		if(BasicUtil.isEmpty(datalink) && null != getContainer()){
			return getContainer().getDatalink();
		}
		return datalink;
	}

	/**
	 * 设置数据源
	 * 当前对象处于容器中时,设置容器数据源
	 * @param dataSource
	 */
	public DataRow setDataSource(String dataSource){
		if(null == dataSource){
			return this;
		}
		if(null  != getContainer()){
			getContainer().setDataSource(dataSource);
		}else{
			this.dataSource = dataSource;
			if(dataSource.contains(".") && !dataSource.contains(":")){
				schema = dataSource.substring(0,dataSource.indexOf("."));
				table = dataSource.substring(dataSource.indexOf(".") + 1);
			}
		}
		return this;
	}
	/**
	 * 子类
	 * @return
	 */
	public Object getChildren(){
		return get(CHILDREN);
	}
	public DataRow setChildren(Object children){
		put(CHILDREN, children);
		return this;
	}
	/**
	 * 父类
	 * @return
	 */
	public Object getParent(){
		return get(PARENT);
	}
	public DataRow setParent(Object parent){
		put(PARENT,parent);
		return this;
	}
	/**
	 * 所有上级数据(递归)
	 * @return
	 */
	public List<Object> getAllParent(){
		if(null != get(ALL_PARENT)){
			return (List<Object>)get(ALL_PARENT);
		}
		List<Object> parents = new ArrayList<Object>();
		Object parent = getParent();
		if(null != parent){
			parents.add(parent);
			if(parent instanceof DataRow){
				DataRow tmp = (DataRow)parent;
				parents.addAll(tmp.getAllParent());
			}
		}
		return parents;
	}
	/**
	 * 转换成对象
	 * @param clazz
	 * @return
	 */
	public <T> T entity(Class<T> clazz){
		T entity = null;
		if(null == clazz){
			return entity;
		}
		try {
			entity = (T)clazz.newInstance();
			/*读取类属性*/
			List<Field> fields = BeanUtil.getFields(clazz);		
			for(Field field:fields){
				if(Modifier.isStatic(field.getModifiers())){
					continue;
				}
				/*取request参数值*/
//				String column = BeanUtil.getColumn(field, false, false);
//				Object value = get(column);
				Object value = get(field.getName());
				/*属性赋值*/
				BeanUtil.setFieldValue(entity, field, value);
			}//end 自身属性
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return entity;
	}
	/**
	 * 是否有指定的key
	 * @param key
	 * @return
	 */
	public boolean has(String key){
		return get(key) != null;
	}
	public boolean hasValue(String key){
		return get(key) != null;
	}
	public boolean hasKey(String key){
		return keys().contains(key);
	}
	public boolean containsKey(String key){
		return keys().contains(key);
	}
	public List<String> keys(){
		List<String> keys = new ArrayList<String>();
		for(Iterator<String> itr=this.keySet().iterator(); itr.hasNext();){
			keys.add(itr.next());
		}
		return keys;
	}
	public DataRow put(KEY_CASE keyCase, String key, Object value){
		if(null != key){
			key = key(keyCase,key);
			if(key.startsWith("+")){
				key = key.substring(1);
				addUpdateColumns(key);
			}
			Object oldValue = get(key);
			if(null == oldValue || !oldValue.equals(value)){
				super.put(key, value);
				if(BasicUtil.isNotEmpty(value)){
					addUpdateColumns(key);
				}
			}
		}
		return this;
	}

	/**
	 * 
	 * @param key
	 * @param value
	 * @param pk		是否是主键
	 * @param override	是否覆盖之前的主键(追加到primaryKeys) 默认覆盖(单一主键)
	 * @return
	 */
	public Object put(KEY_CASE keyCase, String key, Object value, boolean pk, boolean override){
		if(pk){
			if(override){
				primaryKeys.clear();
			}
			this.addPrimaryKey(key);
		}
		this.put(keyCase, key, value);
		return this;
	}
	public Object put(String key, Object value, boolean pk, boolean override){
		return put(KEY_CASE.DEFAULT, key, value, pk, override);
	}
	public Object put(KEY_CASE keyCase, String key, Object value, boolean pk){
		this.put(keyCase, key, value, pk , true);
		return this;
	}
	public Object put(String key, Object value, boolean pk){
		this.put(KEY_CASE.DEFAULT, key, value, pk , true);
		return this;
	}
	@Override
	public Object put(String key, Object value){
		this.put(KEY_CASE.DEFAULT, key, value, false , true);
		return this;
	}
	public Object get(String key){
		Object result = null;
		if(null != key){
			result = super.get(key(key));
		}
		return result;
	}
	public DataRow getRow(String key){
		if(null == key){
			return null;
		}
		Object obj = get(key);
		if(null != obj && obj instanceof DataRow){
			return (DataRow)obj;
		}
		return null;
	}
	public DataSet getSet(String key){
		if(null == key){
			return null;
		}
		Object obj = get(key);
		if(null != obj){
			if(obj instanceof DataSet){
				return (DataSet)obj;
			}else if(obj instanceof List){
				List<?> list = (List<?>)obj;
				DataSet set = new DataSet();
				for(Object item:list){
					set.add(DataRow.parse(item));
				}
				return set;
			}
		}
		return null;
	}
	public List<?> getList(String key){
		if(null == key){
			return null;
		}
		Object obj = get(key);
		if(null != obj && obj instanceof List){
			return (List<?>)obj;
		}
		return null;
	}
	public String getStringNvl(String key, String ... defs){
		String result = getString(key);
		if(BasicUtil.isEmpty(result)){
			if(null == defs || defs.length == 0){
				result = "";
			}else{
				result = BasicUtil.nvl(defs).toString();
			}
		}
		return result;
	}
	public String getString(String key){
		String result = null;
		Object value = get(key);
		if(null != value)
			result = value.toString();
		return result;
	}
	/**
	 * boolean类型true 解析成 1
	 * @param key
	 * @return
	 */
	public int getInt(String key){
		int result = 0;
		try{
			Object val = get(key);
			if(null != val){
				if(val instanceof Boolean && (Boolean)val){
					result = 1;
				}else{
					result = (int)getDouble(key);
				}
			}
		}catch(Exception e){
			result = 0;
		}
		return result;
	}
	public double getDouble(String key){
		double result = 0;
		Object value = get(key);
		try{
			result = Double.parseDouble(value.toString());
		}catch(Exception e){
			result = 0;
		}
		return result;
	}
	public long getLong(String key){
		long result = 0;
		try{
			Object value = get(key);
			result = Long.parseLong(value.toString());
		}catch(Exception e){
			result = 0;
		}
		return result;
	}
	public boolean getBoolean(String key, boolean def){
		return BasicUtil.parseBoolean(getString(key), def);
	}
	public boolean getBoolean(String key){
		return BasicUtil.parseBoolean(getString(key), false);
	}
	public BigDecimal getDecimal(String key){
		BigDecimal result = null;
		try{
			String str = getString(key);
			if(BasicUtil.isNotEmpty(str)){
				result = new BigDecimal(str);
			}
		}catch(Exception e){
			result = null;
		}
		return result;
	}
	public BigDecimal getDecimal(String key, double def){
		return getDecimal(key, new BigDecimal(def));
	}
	public BigDecimal getDecimal(String key, BigDecimal def){
		BigDecimal result = getDecimal(key);
		if(null == result){
			result = def;
		}
		return result;
	}
	public Date getDate(String key, Date def){
		Object date = get(key);
		if(null == date){
			return def;
		}
		if(date instanceof Date){
			return (Date)date;
		}else if(date instanceof Long){
			Date d = new Date();
			d.setTime((Long)date);
			return d;
		}else{
			return DateUtil.parse(date.toString());
		}
	}
	public Date getDate(String key, String def){
		String date = getStringNvl(key, def);
		return DateUtil.parse(date);
	}

	public Date getDate(String key){
		String date = getString(key);
		if(null == date){
			return null;
		}
		return DateUtil.parse(date);
	}
	/**
	 * 转换成json格式
	 * @return
	 */
	public String toJSON(){
		return BeanUtil.map2json(this);
	}
	public DataRow clearEmpty(){
		List<String> keys = keys();
		for(String key:keys){
			if(this.isEmpty(key)){
				this.remove(key);
			}
		}
		return this;
	}
	public DataRow clearNull(){
		List<String> keys = keys();
		for(String key:keys){
			if(null == this.get(key)){
				this.remove(key);
			}
		}
		return this;
	}
	/**
	 * 轮换成xml格式
	 * @return
	 */
	public String toXML(){
		return BeanUtil.map2xml(this);
	}
	public String toXML(boolean border, boolean order){
		return BeanUtil.map2xml(this, border, order);
	}
	/**
	 * 是否处于容器内
	 * @return
	 */
	public boolean hasContainer(){
		if(null != getContainer()){
			return true;
		}else{
			return false;
		}
	}
	/**
	 * 包含当前对象的容器
	 * @return
	 */
	public DataSet getContainer() {
		return container;
	}
	public DataRow setContainer(DataSet container) {
		this.container = container;
		return this;
	}
	public Object getClientTrace() {
		return clientTrace;
	}
	public DataRow setClientTrace(Object clientTrace) {
		this.clientTrace = clientTrace;
		return this;
	}
	public String getSchema() {
		if(null != schema){
			return schema;
		}else{
			DataSet container = getContainer();
			if(null != container){
				return container.getSchema();
			}else{
				return null;
			}
		}
	}
	public DataRow setSchema(String schema) {
		this.schema = schema;
		return this;
	}
	public String getTable() {
		if(null != table){
			return table;
		}else{
			DataSet container = getContainer();
			if(null != container){
				return container.getTable();
			}else{
				return null;
			}
		}
	}
	public DataRow setTable(String table) {
		if(null != table && table.contains(".")){
			String[] tbs = table.split("\\.");
			this.table = tbs[1];
			this.schema = tbs[0];
		}else{
			this.table = table;
		}
		return this;
	}
	/**
	 * 验证是否过期
	 * 根据当前时间与创建时间对比
	 * 过期返回 true
	 * @param expire	过期时间(毫秒)
	 * @return
	 */
	public boolean isExpire(int millisecond){
		if(System.currentTimeMillis() - createTime > millisecond){
			return true;
		}
		return false;
	}
	/**
	 * 是否过期
	 * @param millisecond
	 * @return
	 */
	public boolean isExpire(long millisecond){
		if(System.currentTimeMillis() - createTime > millisecond){
			return true;
		}
		return false;
	}
	public boolean isExpire(){
		if(getExpires() == -1){
			return false;
		}
		if(System.currentTimeMillis() - createTime > getExpires()){
			return true;
		}
		return false;
	}
	/**
	 * 复制数据
	 */
	public Object clone(){
		DataRow row = (DataRow)super.clone();
		row.container = this.container;
		row.primaryKeys = this.primaryKeys;
		row.dataSource = this.dataSource;
		row.schema = this.schema;
		row.table = this.table;
		row.clientTrace = this.clientTrace;
		row.createTime = this.createTime;
		row.isNew = this.isNew;
		return row;
	}
	public Boolean getIsNew() {
		return isNew;
	}
	public DataRow setIsNew(Boolean isNew) {
		this.isNew = isNew;
		return this;
	}
	public List<String> getUpdateColumns() {
		return updateColumns;
	}
	/**
	 * 删除指定的key
	 * @param keys
	 * @return
	 */
	public DataRow remove(String ... keys){
		if(null != keys){
			for(String key:keys){
				if(null != key){
					super.remove(key(key));
				}
				updateColumns.remove(key(key));
			}
		}
		return this;
	}
	/**
	 * 清空需要更新的列
	 * @return
	 */
	public DataRow clearUpdateColumns(){
		updateColumns.clear();
		return this;
	}
	public DataRow removeUpdateColumns(String ... cols){
		if(null != cols){
			for(String col:cols){
				updateColumns.remove(key(col));
			}
		}
		return this;
	}
	/**
	 * 添加需要更新的列
	 * @param cols
	 * @return
	 */
	public DataRow addUpdateColumns(String ... cols){
		if(null != cols){
			for(String col:cols){
				if(!updateColumns.contains(key(col))){
					updateColumns.add(key(col));
				}
			}
		}
		return this;
	}
	public DataRow addAllUpdateColumns(){
		updateColumns.clear();
		updateColumns.addAll(keys());
		return this;
	}
	/**
	 * 将数据从data中复制到this
	 * @param data
	 * @param keys this与data中的key不同时 "this.key:data.key"(CD:ORDER_CD)
	 * @return
	 */
	public DataRow copy(DataRow data, String ... keys){
		if(null == data || null == keys){
			return this;
		}
		for(String key:keys){
			String key1 = key;
			String key2 = key;
			if(key.contains(":")){
				String tmp[] = key.split(":");
				key1 = tmp[0];
				key2 = tmp[1];
			}
			this.put(key1, data.get(key2));
		}
		return this;
	}
	/**
	 * 复制String类型数据
	 * @param data
	 * @param keys
	 * @return
	 */
	public DataRow copyString(DataRow data, String ... keys){
		if(null == data || null == keys){
			return this;
		}
		for(String key:keys){
			String key1 = key;
			String key2 = key;
			if(key.contains(":")){
				String tmp[] = key.split(":");
				key1 = tmp[0];
				key2 = tmp[1];
			}
			Object obj = data.get(key2);
			if(BasicUtil.isNotEmpty(obj)){
				this.put(key1, obj.toString());
			}else{
				this.put(key1, null);
			}
		}
		return this;
	}
	/**
	 * 检测必选项
	 * @param keys
	 * @return
	 */
	public boolean checkRequired(String ... keys){
		List<String> ks = new ArrayList<String>();
		if(null != keys && keys.length >0){
			for(String key:keys){
				ks.add(key);
			}
		}
		return checkRequired(ks);
	}
	public boolean checkRequired(List<String> keys){
		if(null != keys){
			for(String key:keys){
				if(isEmpty(key)){
					return false;
				}
			}
		}
		return true;
	}
	/**
	 * key大小写转换
	 * @param keyCase
	 * @param key
	 * @return
	 */
	private static String keyCase(KEY_CASE keyCase, String key){
		if(null != key){
			if(keyCase == KEY_CASE.DEFAULT){
				if(ConfigTable.IS_UPPER_KEY){
					key = key.toUpperCase();
				}
				if(ConfigTable.IS_LOWER_KEY){
					key = key.toLowerCase();
				}
			}else if(keyCase == KEY_CASE.LOWER){
				key = key.toLowerCase();
			}else if(keyCase == KEY_CASE.UPPER){
				key = key.toUpperCase();
			}
		}
		return key;
	}
	public static String keyCase(String key){
		return keyCase(KEY_CASE.DEFAULT, key);
	}
	private String key(String key){
		return key(KEY_CASE.DEFAULT, key);
	}
	private String key(KEY_CASE keyCase, String key){
		if(keyCase == KEY_CASE.DEFAULT){
			keyCase = this.keyCase;
		}
		return keyCase(keyCase, key);
	}
	/**
	 * 查询条件
	 * @return
	 */
	public Map<String, Object> getQueryParams() {
		if(queryParams.isEmpty()){
			return container.getQueryParams();
		}
		return queryParams;
	}
	/**
	 * 设置查询条件
	 * @param queryParams
	 * @return
	 */
	public DataRow setQueryParams(Map<String, Object> queryParams) {
		this.queryParams = queryParams;
		return this;
	}
	public Object getQueryParam(String key){
		if(queryParams.isEmpty()){
			return container.getQueryParams().get(key);
		}
		return queryParams.get(key);
	}

	public DataRow addQueryParam(String key, Object param) {
		queryParams.put(key,param);
		return this;
	}
	/**
	 * 是否更新null列
	 * @return
	 */
	public boolean isUpdateNullColumn() {
		return updateNullColumn;
	}
	/**
	 * 设置是否更新null列
	 * @param updateNullColumn
	 */
	public void setUpdateNullColumn(boolean updateNullColumn) {
		this.updateNullColumn = updateNullColumn;
	}
	/**
	 * 是否更新空列
	 * @return
	 */
	public boolean isUpdateEmptyColumn() {
		return updateEmptyColumn;
	}
	/**
	 * 设置是否更新空列
	 * @param updateEmptyColumn
	 */
	public void setUpdateEmptyColumn(boolean updateEmptyColumn) {
		this.updateEmptyColumn = updateEmptyColumn;
	}
	
}