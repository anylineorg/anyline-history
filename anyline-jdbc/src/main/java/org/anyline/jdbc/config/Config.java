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


package org.anyline.jdbc.config; 
 
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.anyline.jdbc.config.db.Condition;
import org.anyline.jdbc.config.db.ConditionChain;
import org.anyline.jdbc.config.db.SQL.COMPARE_TYPE;
 
public interface Config extends Serializable {
	//从request 取值方式 
	public static int FETCH_REQUEST_VALUE_TYPE_SINGLE = 1;	//单值 
	public static int FETCH_REQUEST_VALUE_TYPE_MULIT  = 2;	//数组 
	public void setValue(Map<String,Object> values); 
	public List<Object> getValues() ; 
	public List<Object> getOrValues() ; 
	public void addValue(Object value);
	public void setValue(Object value); 
 
	/** 
	 *  
	 * @param chain 容器 
	 * @return return
	 */ 
	public Condition createAutoCondition(ConditionChain chain);

	public String getPrefix() ; 	//XML condition.id 或表名/表别名
 
	public void setPrefix(String prefix) ;

	public String getVariable() ;//XML condition中的key 或列名

	public void setVariable(String variable) ;

	public String getKey() ;//参数key

	public void setKey(String key) ;


	public COMPARE_TYPE getCompare() ; 
	public void setCompare(COMPARE_TYPE compare) ; 
	
	public COMPARE_TYPE getOrCompare() ; 
	public void setOrCompare(COMPARE_TYPE compare) ; 
 
	public boolean isEmpty() ; 
 
	public void setEmpty(boolean empty) ; 
 
	public boolean isRequire() ; 
	public boolean isStrictRequired(); 
	public void setRequire(boolean require) ;
	public void setStrictRequired(boolean require);

	public String getJoin() ; 
 
	public void setJoin(String join) ; 
 
	public boolean isKeyEncrypt() ; 
 
	public boolean isValueEncrypt();
	
	public Object clone();
	public String toString();
	public String cacheKey(); 
}
