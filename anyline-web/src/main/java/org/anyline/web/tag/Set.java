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


package org.anyline.web.tag;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;

import org.anyline.entity.DataSet;
import org.anyline.util.BasicUtil;
import org.apache.log4j.Logger;

public class Set extends BaseBodyTag {
	private static final long serialVersionUID = 1L;
	private static final Logger log = Logger.getLogger(Set.class);
	private String scope;
	private Object data;
	private String selector;
	private String var;
	private int index = -1;

	public int doEndTag() throws JspException {
		HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
		try {
			if (null != data) {
				if (data instanceof String) {
					if (data.toString().endsWith("}")) {
						data = data.toString().replace("{", "").replace("}", "");
					} else {
						if ("servelt".equals(scope) || "application".equalsIgnoreCase(scope)) {
							data = request.getSession().getServletContext().getAttribute(data.toString());
						} else if ("session".equals(scope)) {
							data = request.getSession().getAttribute(data.toString());
						}  else if ("session".equals(scope)) {
							data = request.getAttribute(data.toString());
						}else {
							data = pageContext.getAttribute(data.toString());
						}
					}
				}
				if(!(data instanceof Collection)){
					return EVAL_PAGE;
				}
				if(BasicUtil.isNotEmpty(selector) && data instanceof DataSet){
					DataSet set = (DataSet)data;
					data = set.getRows(selector.split(","));
				}

				if(index !=-1 && data instanceof Collection){
					Collection items = (Collection) data;
					int i = 0;
					for(Object item:items){
						if(index ==i){
							data = item;
							break;
						}
						i ++;
					}
				}
				if ("servelt".equals(scope) || "application".equalsIgnoreCase(scope)) {
					request.getSession().getServletContext().setAttribute(var,data);
				} else if ("session".equals(scope)) {
					request.getSession().setAttribute(var,data);
				}  else if ("session".equals(scope)) {
					request.setAttribute(var,data);
				}else {
					pageContext.setAttribute(var,data);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			release();
		}
		return EVAL_PAGE;
	}


	public Object getData() {
		return data;
	}


	public void setData(Object data) {
		this.data = data;
	}




	@Override
	public void release() {
		super.release();
		scope = null;
		data = null;
		var = null;
		selector = null;
		index = -1;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}


	public String getSelector() {
		return selector;
	}


	public void setSelector(String selector) {
		this.selector = selector;
	}
	
}