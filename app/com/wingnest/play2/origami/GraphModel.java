/*
 * Copyright since 2013 Shigeru GOUGI
 *                              e-mail:  sgougi@gmail.com
 *                              twitter: @igerugo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wingnest.play2.origami;

import static com.wingnest.play2.origami.plugin.OrigamiLogger.*;

import java.lang.reflect.Field;
import java.util.Date;

import javax.persistence.Transient;

import com.wingnest.play2.origami.annotations.DisupdateFlag;
import com.wingnest.play2.origami.annotations.SmartDate;
import com.wingnest.play2.origami.annotations.SmartDateType;
import com.wingnest.play2.origami.plugin.exceptions.OrigamiUnexpectedException;

import com.orientechnologies.orient.core.db.graph.OGraphDatabase;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;

public abstract class GraphModel {

	@Transient
	protected ORID orid = null;
	@Transient
	private ODocument doc = null;

	public ODocument getDocument() {
		if ( !loadDocument() ) {
			doc = createModel();
		}
		return doc;
	}

	public String getSchemaName() {
		return this.getClass().getSimpleName();
	}

	public ORID getORID() {
		return orid;
	}

	@SuppressWarnings("unchecked")
	public <T extends GraphModel> T save() {
		try {
			SmartDateUtils.enhance(this);
			_save();
			return (T) this;
		} catch ( RuntimeException e ) {
			error("GraphModel.save: class = %s, orid = %s: %s", this.getClass().getName(), this.getORID() != null ? this.getORID().toString() : "null", e.getMessage());
			throw new OrigamiUnexpectedException(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T extends GraphModel> T delete() {
		_delete();
		return (T) this;
	}

	public boolean isUnmanaged() {
		return getORID() == null;
	}

	final public Object getORIDAsString() {
		final ORID aOrid = getORID();
		if ( aOrid == null )
			return null;
		return aOrid.toString();
	}

	@Override
	public boolean equals(Object other) {
		if ( other == null ) {
			return false;
		}
		if ( this == other ) {
			return true;
		}
		if ( !this.getClass().isAssignableFrom(other.getClass()) ) {
			return false;
		}
		if ( this.getORIDAsString() == null ) {
			return false;
		}
		return this.getORIDAsString().equals(((GraphModel) other).getORIDAsString());
	}
	
	////

	abstract protected void _delete();

	abstract protected void _save();
	
	abstract protected ODocument createModel();

	protected static OGraphDatabase db() {
		return GraphDB.open();
	}

	////
	
	private boolean loadDocument() {
		if ( orid != null && doc == null ) {
			doc = db().load(orid);
		} else if ( doc == null ) {
			orid = null;
		}
		return doc != null;
	}
	
	////

	private static class SmartDateUtils {

		public static void enhance(GraphModel g) {
			if ( g == null )
				return;
			final Field[] fields = g.getClass().getFields();
			for ( final Field field : fields ) {
				doEnhance(field, g, 0);
			}
		}

		private static void doEnhance(Field field, Object g, int depth) {
			final SmartDate sd = field.getAnnotation(SmartDate.class);
			if ( sd != null ) {
				final SmartDateType sdt = sd.type();
				try {
					if ( sdt.equals(SmartDateType.CreatedDate) ) {
						if ( Date.class.isAssignableFrom(field.getType()) ) {
							Object obj = field.get(g);
							if ( obj == null )
								field.set(g, new Date());
						}
					} else if ( sdt.equals(SmartDateType.UpdatedDate) ) {
						if ( Date.class.isAssignableFrom(field.getType()) ) {
							field.setAccessible(true);
							Object obj = field.get(g);
							if ( obj == null ) {
								field.set(g, new Date());
							} else if ( !hasDisupdateFlag(g) ) {
								field.set(g, new Date());
							}
						}
					}
				} catch ( Exception e ) {
					debug("SmartDateUtils#doEnhance : %s, %s", e.getClass().getName(), e.getMessage());
					throw new OrigamiUnexpectedException(e);
				}
			}
		}

		private static boolean hasDisupdateFlag(Object g) {
			final Field[] fields = g.getClass().getFields();
			for ( final Field field : fields ) {
				final DisupdateFlag disupdateFlag = field.getAnnotation(DisupdateFlag.class);
				if ( disupdateFlag != null ) {
					field.setAccessible(true);
					try {
						final boolean b = field.getBoolean(g);
						debug("######>> hasDisupdateFlag : found DisupdateFlag Annotation : value = " + b);
						return b;
					} catch ( Exception e ) {
						throw new OrigamiUnexpectedException(e);
					}
				}
			}
			return false;
		}

	}

}
