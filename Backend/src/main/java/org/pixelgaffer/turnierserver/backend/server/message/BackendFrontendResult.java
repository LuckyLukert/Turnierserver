/*
 * BackendFrontendResult.java
 *
 * Copyright (C) 2015 Pixelgaffer
 *
 * This work is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or any later
 * version.
 *
 * This work is distributed in the hope that it will be useful, but without
 * any warranty; without even the implied warranty of merchantability or
 * fitness for a particular purpose. See version 2 and version 3 of the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.pixelgaffer.turnierserver.backend.server.message;

import java.io.PrintWriter;
import java.io.StringWriter;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class BackendFrontendResult
{
	/** Die request ID des Frontends. */
	@Getter
	public int requestid;
	
	/** Gibt an ob der Job erfolgreich war. */
	@Getter
	public boolean success;
	
	/** Die Exception, die beim Bearbeiten aufgetreten ist, oder null. */
	@Getter
	private String exception;
	
	public BackendFrontendResult (int requestid, boolean success)
	{
		this.requestid = requestid;
		this.success = success;
	}
	
	public BackendFrontendResult (int requestid, boolean success, Exception exception)
	{
		this.requestid = requestid;
		this.success = success;
		if (exception != null)
		{
			StringWriter sw = new StringWriter();
			exception.printStackTrace(new PrintWriter(sw));
			this.exception = sw.toString();
		}
	}
}
