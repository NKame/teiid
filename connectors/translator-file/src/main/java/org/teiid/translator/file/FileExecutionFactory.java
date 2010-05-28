/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.translator.file;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.resource.ResourceException;
import javax.resource.cci.ConnectionFactory;

import org.teiid.core.BundleUtil;
import org.teiid.core.TeiidRuntimeException;
import org.teiid.core.types.BlobImpl;
import org.teiid.core.types.BlobType;
import org.teiid.core.types.ClobImpl;
import org.teiid.core.types.ClobType;
import org.teiid.core.types.InputStreamFactory.FileInputStreamFactory;
import org.teiid.language.Call;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;
import org.teiid.metadata.MetadataFactory;
import org.teiid.metadata.Procedure;
import org.teiid.metadata.RuntimeMetadata;
import org.teiid.metadata.ProcedureParameter.Type;
import org.teiid.translator.DataNotAvailableException;
import org.teiid.translator.ExecutionContext;
import org.teiid.translator.ExecutionFactory;
import org.teiid.translator.FileConnection;
import org.teiid.translator.MetadataProvider;
import org.teiid.translator.ProcedureExecution;
import org.teiid.translator.Translator;
import org.teiid.translator.TranslatorException;
import org.teiid.translator.TranslatorProperty;
import org.teiid.translator.TypeFacility;

@Translator(name="file")
public class FileExecutionFactory extends ExecutionFactory implements MetadataProvider {
	
	private final class FileProcedureExecution implements ProcedureExecution {
		private final Call command;
		private final FileConnection fc;
		private File[] files = null;
		boolean isText = false;
		private int index;

		private FileProcedureExecution(Call command, FileConnection fc) {
			this.command = command;
			this.fc = fc;
		}

		@Override
		public void execute() throws TranslatorException {
			files = FileConnection.Util.getFiles((String)command.getArguments().get(0).getArgumentValue().getValue(), fc);
			String name = command.getProcedureName();
			if (name.equalsIgnoreCase(GETTEXTFILES)) {
				isText = true;
			} else if (!name.equalsIgnoreCase(GETFILES)) {
				throw new TeiidRuntimeException("Unknown procedure name " + name); //$NON-NLS-1$
			}
		}

		@Override
		public void close() {
			try {
				fc.close();
			} catch (ResourceException e) {
				LogManager.logDetail(LogConstants.CTX_CONNECTOR, e, "Exception closing"); //$NON-NLS-1$
			}
		}

		@Override
		public void cancel() throws TranslatorException {
			
		}

		@Override
		public List<?> next() throws TranslatorException, DataNotAvailableException {
			if (files == null || index >= files.length) {
				return null;
			}
			ArrayList<Object> result = new ArrayList<Object>(2);
			final File file = files[index++];
			FileInputStreamFactory isf = new FileInputStreamFactory(file, encoding);
			isf.setLength(file.length());
			Object value = null;
			if (isText) {
				value = new ClobType(new ClobImpl(isf, -1));
			} else {
				value = new BlobType(new BlobImpl(isf));
			}
			result.add(value);
			result.add(file.getName());
			return result;
		}

		@Override
		public List<?> getOutputParameterValues() throws TranslatorException {
			return Collections.emptyList();
		}
	}

	public static BundleUtil UTIL = BundleUtil.getBundleUtil(FileExecutionFactory.class);
	
	public static final String GETTEXTFILES = "getTextFiles"; //$NON-NLS-1$
	public static final String GETFILES = "getFiles"; //$NON-NLS-1$
	
	private String encoding = Charset.defaultCharset().name();
	
	@TranslatorProperty(display="File Encoding",advanced=true)
	public String getEncoding() {
		return encoding;
	}
	
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
	
	//@Override
	public ProcedureExecution createProcedureExecution(final Call command,
			final ExecutionContext executionContext, final RuntimeMetadata metadata,
			Object connectionFactory) throws TranslatorException {
		final FileConnection fc;
		try {
			fc = (FileConnection)((ConnectionFactory)connectionFactory).getConnection();
		} catch (ResourceException e) {
			throw new TranslatorException(e);
		}
		return new FileProcedureExecution(command, fc);
	}

	@Override
	public void getConnectorMetadata(MetadataFactory metadataFactory, Object connectionFactory) throws TranslatorException {
		Procedure p = metadataFactory.addProcedure(GETTEXTFILES); 
		metadataFactory.addProcedureParameter("path", TypeFacility.RUNTIME_NAMES.STRING, Type.In, p); //$NON-NLS-1$
		metadataFactory.addProcedureResultSetColumn("file", TypeFacility.RUNTIME_NAMES.CLOB, p); //$NON-NLS-1$
		metadataFactory.addProcedureResultSetColumn("path", TypeFacility.RUNTIME_NAMES.STRING, p); //$NON-NLS-1$
		
		Procedure p1 = metadataFactory.addProcedure(GETFILES);
		metadataFactory.addProcedureParameter("path", TypeFacility.RUNTIME_NAMES.STRING, Type.In, p1); //$NON-NLS-1$
		metadataFactory.addProcedureResultSetColumn("file", TypeFacility.RUNTIME_NAMES.BLOB, p1); //$NON-NLS-1$
		metadataFactory.addProcedureResultSetColumn("path", TypeFacility.RUNTIME_NAMES.STRING, p1); //$NON-NLS-1$
	} 
	
}
