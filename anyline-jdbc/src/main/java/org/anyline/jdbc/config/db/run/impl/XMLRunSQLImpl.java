/*  
 * Copyright 2006-2020 www.anyline.org
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
 
 
package org.anyline.jdbc.config.db.run.impl; 
 
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;

import org.anyline.entity.PageNavi;
import org.anyline.jdbc.config.ConfigParser;
import org.anyline.jdbc.config.ParseResult;
import org.anyline.jdbc.config.db.Condition;
import org.anyline.jdbc.config.db.ConditionChain;
import org.anyline.jdbc.config.db.Group;
import org.anyline.jdbc.config.db.GroupStore;
import org.anyline.jdbc.config.db.Order;
import org.anyline.jdbc.config.db.OrderStore;
import org.anyline.jdbc.config.db.SQL;
import org.anyline.jdbc.config.db.SQLVariable;
import org.anyline.jdbc.config.db.impl.GroupStoreImpl;
import org.anyline.jdbc.config.db.impl.OrderStoreImpl;
import org.anyline.jdbc.config.db.run.RunSQL;
import org.anyline.jdbc.config.db.sql.auto.impl.AutoConditionImpl;
import org.anyline.jdbc.config.db.sql.xml.impl.XMLConditionChainImpl;
import org.anyline.jdbc.config.Config;
import org.anyline.jdbc.config.ConfigStore;
import org.anyline.jdbc.ognl.DefaultMemberAccess;
import org.anyline.util.BasicUtil;
 
public class XMLRunSQLImpl extends BasicRunSQLImpl implements RunSQL{ 
	private List<String> conditions; 
	private List<String> staticConditions; 
	public XMLRunSQLImpl(){ 
		this.conditionChain = new XMLConditionChainImpl(); 
		this.orderStore = new OrderStoreImpl(); 
		this.groupStore = new GroupStoreImpl(); 
	} 
 
	public RunSQL setSql(SQL sql){ 
		this.sql = sql; 
		copyParam(); 
		return this; 
	} 
	public void init(){ 
		super.init(); 
		if(null != configStore){ 
			for(Config conf:configStore.getConfigChain().getConfigs()){ 
				setConditionValue(conf.isRequire(),  
						conf.isStrictRequired(), conf.getPrefix(), conf.getVariable(), conf.getValues(), conf.getCompare());
			} 
			 
			OrderStore orderStore = configStore.getOrders(); 
			if(null != orderStore){ 
				List<Order> orders = orderStore.getOrders(); 
				if(null != orders){ 
					for(Order order:orders){ 
						this.orderStore.order(order); 
					} 
				} 
			} 
			PageNavi navi = configStore.getPageNavi(); 
			if(navi != null){ 
				this.pageNavi = navi; 
			} 
		} 
		//condition?????? 
		if(null != conditions){ 
			for(String condition:conditions){ 
				ParseResult parser = ConfigParser.parse(condition,false); 
				Object value = ConfigParser.getValues(parser);// parser.getKey(); 
				if(parser.getParamFetchType() == ParseResult.FETCH_REQUEST_VALUE_TYPE_MULIT){ 
					String[] tmp = value.toString().split(","); 
					if(null != tmp){ 
						List<String> list = new ArrayList<String>(); 
						for(String item:tmp){ 
							list.add(item); 
						} 
						value = list; 
					} 
					 
				} 
				setConditionValue(parser.isRequired(), parser.isStrictRequired(), parser.getPrefix(), parser.getVar(), value, parser.getCompare());
			} 
		} 
		//??????????????????required strictRequired 
		for(Condition con:conditionChain.getConditions()){ 
			if(!con.isActive()){//????????????value?????? 
				if(con.isRequired()){ 
					con.setActive(true); 
					List<SQLVariable> vars = con.getVariables(); 
					if(null != vars){ 
						for(SQLVariable var:vars){ 
							var.setValue(false,null); 
						} 
					} 
				} 
				if(con.isStrictRequired()){ 
					log.warn("[valid:false][con:{}]",con.getId());
					this.valid = false; 
				} 
			} 
		} 
		GroupStore groupStore = sql.getGroups(); 
		if(null != groupStore){ 
			List<Group> groups = groupStore.getGroups(); 
			if(null != groups){ 
				for(Group group:groups){ 
					this.groupStore.group(group); 
				} 
			} 
		} 
		checkTest(); 
		parseText();
		checkValid();
	}

	private void checkValid(){ 
		if(!valid){ 
			return; 
		} 
		if(null != variables){ 
			for(SQLVariable var:variables){ 
				if(var.isRequired() || var.isStrictRequired()){ 
					if(BasicUtil.isEmpty(true,var.getValues())){ 
						log.warn("[valid:false][var:{}]",var.getKey());
						this.valid = false; 
						return; 
					} 
				} 
			} 
		} 
		if(null != conditionChain && !conditionChain.isValid()){ 
			this.valid = false; 
			return; 
		} 
	} 
	protected void parseText(){ 
		String result = sql.getText(); 
		if(null != variables){ 
			for(SQLVariable var:variables){ 
				if(null == var){ 
					continue; 
				} 
				if(var.getType() == SQLVariable.VAR_TYPE_REPLACE){ 
					// CD = ::CD 
					// CD = ${CD} 
					List<Object> varValues = var.getValues(); 
					String value = null; 
					if(BasicUtil.isNotEmpty(true,varValues)){ 
						value = (String)varValues.get(0); 
						if(null != value){ 
							value = value.replace("'", "").replace("%", ""); 
						} 
					} 
					String replaceKey = ""; 
					if(var.getSignType() ==1){ 
						replaceKey = "::" + var.getKey(); 
					}else if(var.getSignType() ==2){ 
						replaceKey = "${" + var.getKey() + "}"; 
					} 
					if(null != value){ 
						result = result.replace(replaceKey, value); 
					}else{ 
						result = result.replace(replaceKey, "NULL"); 
					} 
				} 
			} 
			for(SQLVariable var:variables){ 
				if(null == var){ 
					continue; 
				} 
				if(var.getType() == SQLVariable.VAR_TYPE_KEY_REPLACE){ 
					//CD = ':CD' 
					//CD = '{CD}' 
					List<Object> varValues = var.getValues(); 
					String value = null; 
					if(BasicUtil.isNotEmpty(true,varValues)){ 
						value = (String)varValues.get(0); 
						if(null != value){ 
							value = value.replace("'", "").replace("%", ""); 
						} 
					} 
 
					String replaceKey = ""; 
					if(var.getSignType() ==1){ 
						replaceKey = ":" + var.getKey(); 
					}else if(var.getSignType() ==2){ 
						replaceKey = "{" + var.getKey() + "}"; 
					} 
					if(null != value){ 
						result = result.replace(replaceKey, value); 
					}else{ 
						result = result.replace(replaceKey, ""); 
					} 
				} 
			} 
			for(SQLVariable var:variables){ 
				if(null == var){ 
					continue; 
				} 
				if(var.getType() == SQLVariable.VAR_TYPE_KEY){ 
					// CD = :CD 
					// CD = {CD} 
					// CD like '%:CD%' 
					// CD like '%{CD}%' 
					List<Object> varValues = var.getValues(); 
					if(BasicUtil.isNotEmpty(true, varValues)){ 
 
						String replaceKey = ""; 
						if(var.getSignType() ==1){ 
							replaceKey = ":" + var.getKey(); 
						}else if(var.getSignType() ==2){ 
							replaceKey = "{" + var.getKey() + "}"; 
						} 
						if(var.getCompare() == SQL.COMPARE_TYPE.LIKE){ 
							//CD LIKE '%{CD}%' > CD LIKE concat('%',?,'%') || CD LIKE '%' + ? + '%' 
							result = result.replace("'%"+replaceKey+"%'", creater.concat("'%'","?","'%'")); 
							addValues(varValues.get(0)); 
						}else if(var.getCompare() == SQL.COMPARE_TYPE.LIKE_SUBFIX){ 
							result = result.replace("'%"+replaceKey+"'", creater.concat("'%'","?")); 
							addValues(varValues.get(0)); 
						}else if(var.getCompare() == SQL.COMPARE_TYPE.LIKE_PREFIX){ 
							result = result.replace("'"+replaceKey+"%'", creater.concat("?","'%'")); 
							addValues(varValues.get(0)); 
						}else if(var.getCompare() == SQL.COMPARE_TYPE.IN){ 
							//?????????IN 
							String replaceDst = "";  
							for(Object tmp:varValues){ 
								addValues(tmp); 
								replaceDst += " ?"; 
							} 
							replaceDst = replaceDst.trim().replace(" ", ","); 
							result = result.replace(replaceKey, replaceDst); 
						}else{ 
							//????????? 
							result = result.replace(replaceKey, "?"); 
							addValues(varValues.get(0)); 
						} 
					} 
				} 
			} 
			//????????????????????? 
			for(SQLVariable var:variables){ 
				if(null == var){ 
					continue; 
				} 
				//CD = ? 
				if(var.getType() == SQLVariable.VAR_TYPE_INDEX){ 
					List<Object> varValues = var.getValues(); 
					String value = null; 
					if(BasicUtil.isNotEmpty(true, varValues)){ 
						value = (String)varValues.get(0); 
					} 
					addValues(value); 
				} 
			} 
		}

		builder.append(result);
		appendCondition(); 
		appendStaticCondition(); 
		appendGroup(); 
		//appendOrderStore();
	} 
 
	private void copyParam(){ 
		//??????XML SQL ?????? 
		List<SQLVariable> xmlVars = sql.getSQLVariables(); 
		if(null != xmlVars){ 
			if(null == this.variables){ 
				variables = new ArrayList<SQLVariable>(); 
			} 
			for(SQLVariable var:xmlVars){ 
				if(null == var){ 
					continue; 
				} 
				try{ 
					variables.add((SQLVariable)var.clone()); 
				}catch(Exception e){ 
					e.printStackTrace(); 
				} 
			} 
		} 
		//??????XML SQL ???????????? 
		ConditionChain xmlConditionChain = sql.getConditionChain(); 
		if(null != xmlConditionChain){ 
			if(null == this.conditionChain){ 
				this.conditionChain = new XMLConditionChainImpl(); 
			} 
			List<Condition> conditions = xmlConditionChain.getConditions(); 
			if(null != conditions){ 
				for(Condition condition:conditions){ 
					if(null == condition){ 
						continue; 
					} 
					try{ 
						this.conditionChain.addCondition((Condition)condition.clone()); 
					}catch(Exception e){ 
						e.printStackTrace(); 
					} 
				} 
			} 
		} 
		//??????XML SQL ORDER 
		OrderStore xmlOrderStore = sql.getOrders(); 
		if(null != xmlOrderStore){ 
			List<Order> xmlOrders = xmlOrderStore.getOrders(); 
			if(null != xmlOrders){ 
				for(Order order:xmlOrders){ 
					this.orderStore.order(order); 
				} 
			} 
		} 
		//?????? XML SQL GROUP 
		GroupStore xmlGroupStore = sql.getGroups(); 
		if(null != xmlGroupStore){ 
			List<Group> xmlGroups = xmlGroupStore.getGroups(); 
			if(null != xmlGroups){ 
				for(Group group:xmlGroups){ 
					this.groupStore.group(group); 
				} 
			} 
		} 
				 
	} 
	private void appendGroup(){ 
		if(null != groupStore){
			builder.append(groupStore.getRunText(disKeyFr+disKeyTo));
		} 
	} 
	/** 
	 * ??????test????????? 
	 */ 
	@SuppressWarnings("rawtypes")
	private void checkTest(){ 
		if(null != conditionChain){ 
			for(Condition con:conditionChain.getConditions()){ 
				String test = con.getTest(); 
 
				if(null != test){ 
					Map<String,Object> map = con.getRunValuesMap(); 
					Map<String,Object> runtimeValues = new HashMap<String,Object>();
					//????????????????????????0?????? ognl???????????????
					for(Map.Entry<String, Object> entry : map.entrySet()){
					    String mapKey = entry.getKey();
					    Object mapValue = entry.getValue();
					    if(null != mapValue && mapValue instanceof Collection){
					    	Collection cols = (Collection)mapValue;
					    	for(Object obj:cols){
					    		runtimeValues.put(mapKey, obj);
					    		break;
					    	}
					    }
					}
					try { 
						OgnlContext context = new OgnlContext(null, null, new DefaultMemberAccess(true));
						Boolean result = (Boolean) Ognl.getValue(test,context, runtimeValues); 
						if(!result){ 
							con.setActive(false); 
						}else{ 
							if(con.getVariableType() == Condition.VARIABLE_FLAG_TYPE_NONE){ 
								con.setActive(true); 
								conditionChain.setActive(true); 
							} 
						} 
					} catch (OgnlException e) { 
						e.printStackTrace(); 
					} 
				}else{ 
					//???test?????? 
					if(con.getVariableType() == Condition.VARIABLE_FLAG_TYPE_NONE){ 
						con.setActive(true); 
						conditionChain.setActive(true); 
					} 
				} 
			} 
		} 
	} 
	/** 
	 * ??????????????????
	 */ 
	private void appendCondition(){ 
		if(null == conditionChain || !conditionChain.isActive()){ 
			return; 
		} 
		if(!endwithWhere(builder.toString())){
			builder.append(" WHERE 1=1");
		}
		builder.append(conditionChain.getRunText(creater));
		addValues(conditionChain.getRunValues()); 
//		if(null != staticConditions){ 
//			for(String con:staticConditions){ 
//				query.append("\nAND ").append(con); 
//			} 
//		} 
	} 
	private void appendStaticCondition(){ 
		if(!endwithWhere(builder.toString())){
			builder.append(" WHERE 1=1");
		} 
		if(null != staticConditions){ 
			for(String con:staticConditions){
				builder.append("\nAND ").append(con);
			} 
		} 
	} 
	 
	public void setConfigs(ConfigStore configs) { 
		this.configStore = configs; 
		if(null != configs){ 
			this.pageNavi = configs.getPageNavi(); 
			 
		} 
	} 
 
	private SQLVariable getVariable(String key){ 
		if(null != variables){ 
			for(SQLVariable v:variables){ 
				if(null == v){ 
					continue; 
				} 
				if(v.getKey().equalsIgnoreCase(key)){ 
					return v; 
				} 
			} 
		} 
		return null; 
	} 
	private List<SQLVariable> getVariables(String key){ 
		List<SQLVariable> vars = new ArrayList<SQLVariable>(); 
		if(null != variables){ 
			for(SQLVariable v:variables){ 
				if(null == v){ 
					continue; 
				} 
				if(v.getKey().equalsIgnoreCase(key)){ 
					vars.add(v); 
				} 
			} 
		} 
		return vars; 
	}

	/**
	 *
	 * @param required ????????????
	 * @param strictRequired ????????????????????????
	 * @param	prefix  ????????????ID
	 * @param	variable  ??????|??????key
	 * @param	value  ???
	 * @param compare ????????????
	 * @return RunSQL
	 */
	@Override
	public RunSQL setConditionValue(boolean required, boolean strictRequired, String prefix, String variable, Object value, SQL.COMPARE_TYPE compare) {
		/*?????????condition.id???condition.id = variable ???,??????var???SQL??????????????????*/
		//?????????var ?????????condition
		if(null != variables &&  
				(BasicUtil.isEmpty(prefix) || prefix.equals(variable))
		){ 
			List<SQLVariable> vars = getVariables(variable);
			for(SQLVariable var:vars){ 
				var.setValue(value); 
			} 
		} 
		/*????????????*/ 
		if(null == variable){
			return this; 
		}
		Condition con = null;
		if(null == prefix){
			con = getCondition(variable);
		}else{
			con = getCondition(prefix);;
		}

		SQLVariable var = getVariable(variable);
		if(null == con && null == var){//???????????????condition??????????????????text???????????? 
			if(this.isStrict()){ 
				return this; 
			}else{ 
				//??????????????? 
//				String column = variable;
//				//String condition, String variable
//				if(BasicUtil.isNotEmpty(prefix) && !prefix.equals(variable)){
//					column = prefix + "." + variable;
//				}
				Condition newCon = new AutoConditionImpl(required, strictRequired,prefix, variable, value, compare);
				conditionChain.addCondition(newCon); 
				if(newCon.isActive()){ 
					conditionChain.setActive(true); 
				} 
			} 
			return this; 
		} 
		if(null != con){ 
			con.setValue(variable, value); 
			if(con.isActive()){ 
				this.conditionChain.setActive(true); 
			} 
		} 
		return this; 
	} 
	@Override 
	public RunSQL setConditionValue(boolean required, String condition, String variable, Object value, SQL.COMPARE_TYPE compare) { 
		return setConditionValue(required, false, condition, variable, value, compare); 
	} 
	 
		 
	public RunSQL addConditions(String[] conditions) { 
		/*??????????????????*/ 
		if(null != conditions){ 
			for(String condition:conditions){ 
				if(null == condition){ 
					continue; 
				}
				condition = condition.trim(); 
				String up = condition.toUpperCase().replaceAll("\\s+", " ").trim(); 
				if(up.startsWith("ORDER BY")){ 
					String orderStr = condition.substring(up.indexOf("ORDER BY") + "ORDER BY".length()).trim(); 
					String orders[] = orderStr.split(","); 
					for(String item:orders){ 
						//sql.order(item); 
						if(null != configStore){ 
							configStore.order(item); 
						} 
						if(null != this.orderStore){ 
							this.orderStore.order(item); 
						} 
					} 
					continue; 
				}else if(up.startsWith("GROUP BY")){ 
					String groupStr = condition.substring(up.indexOf("GROUP BY") + "GROUP BY".length()).trim(); 
					String groups[] = groupStr.split(","); 
					for(String item:groups){ 
						//sql.group(item); 
						if(null != configStore){ 
							configStore.group(item); 
						} 
					} 
					continue; 
				} 
				addCondition(condition); 
			} 
		} 
		return this; 
	} 
 
	public void addSatticCondition(String condition){ 
		if(null == staticConditions){ 
			staticConditions = new ArrayList<String>(); 
		} 
		if(!isStrict()){ 
			staticConditions.add(condition); 
		} 
	} 
	public RunSQL addCondition(String condition) { 
		if(BasicUtil.isEmpty(condition)){ 
			return this; 
		} 
 
		if(condition.startsWith("{") && condition.endsWith("}")){ 
			//??????SQL  ????????? 
			addSatticCondition(condition.substring(1, condition.length()-1)); 
			return this; 
		} 
		if(condition.contains(":")){ 
			//:???????????????????????? 
			boolean isTime = false; 
			int idx = condition.indexOf(":"); 
			//''?????? 
			if(condition.indexOf("'")<idx && condition.indexOf("'", idx+1) > 0){ 
				isTime = true; 
			} 
			if(!isTime){			 
				//???????????????SQL 
				if(null == conditions){ 
					conditions = new ArrayList<String>(); 
				} 
				conditions.add(condition); 
				return this; 
			} 
		} 
		addSatticCondition(condition); 
		return this; 
	} 
 
 
	/** 
	 * ??????????????? 
	 * @param obj  obj
	 * @return return
	 */ 
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public RunSQL addValues(Object obj){ 
		if(null == obj){ 
			return this; 
		} 
		if(null == values){ 
			values = new ArrayList<Object>(); 
		} 
		if(obj instanceof Collection){ 
			values.addAll((Collection)obj); 
		}else{ 
			values.add(obj); 
		} 
		return this; 
	} 
	 
	public RunSQL addOrders(OrderStore orderStore){ 
		if(null == orderStore){ 
			return this; 
		} 
		List<Order> orders = orderStore.getOrders(); 
		if(null == orders){ 
			return this; 
		} 
		for(Order order:orders){ 
			this.orderStore.order(order); 
		} 
		return this; 
	} 
	public RunSQL addOrder(Order order){ 
		this.orderStore.order(order); 
		return this; 
	} 
	 
	 
	 
 
 
	/* ****************************************************************************************** 
	 *  
	 * 										???????????? 
	 *  
	 * *******************************************************************************************/ 
 
	/** 
	 * ?????????????????????????????? 
	 * @param prefix  condition.id
	 * @param variable variable
	 * @param value value
	 * @return return
	 */
	public RunSQL addCondition(String prefix, String variable, Object value) {
		if(null != variables && BasicUtil.isEmpty(variable)){ 
			for(SQLVariable v:variables){ 
				if(null == v){ 
					continue; 
				} 
				if(v.getKey().equalsIgnoreCase(prefix)){
					v.setValue(value); 
				} 
			} 
		} 
		/*????????????*/ 
		if(null == prefix){
			return this; 
		} 
		Condition con = getCondition(prefix);
		if(null == con){ 
			return this; 
		} 
		variable = BasicUtil.nvl(variable, prefix).toString();
		con.setValue(variable, value); 
		return this; 
	} 
 
	public void setConfigStore(ConfigStore configStore) { 
		this.configStore = configStore; 
	} 
	public RunSQL addCondition(boolean required, boolean strictRequired, String column, Object value, SQL.COMPARE_TYPE compare){ 
		setConditionValue(required, strictRequired, column, null, value, compare); 
		return this; 
	} 
	public RunSQL addCondition(boolean required, String column, Object value, SQL.COMPARE_TYPE compare){ 
		return addCondition(required, false, column, value,compare); 
	} 
	 
} 
