package de.hterhors.obie.tools.ml.templates.scope;

import java.util.Collections;
import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;

import de.hterhors.obie.tools.ml.templates.AbstractOBIETemplate;
import de.uni.bielefeld.sc.hterhors.psink.obie.core.ontology.interfaces.IOBIEThing;
import factors.FactorScope;

public class OBIEFactorScope extends FactorScope {

	final private Set<Class<? extends IOBIEThing>> influencedVariables;
	final private Class<? extends IOBIEThing> entityRootClassType;

	@Deprecated
	public OBIEFactorScope(Set<Class<? extends IOBIEThing>> influencedVariables,
			Class<? extends IOBIEThing> entityRootClassType, AbstractOBIETemplate<?> template, Object... variables) {
		super(template, variables);
		this.entityRootClassType = entityRootClassType;
		this.influencedVariables = influencedVariables;
	}

	public OBIEFactorScope(AbstractOBIETemplate<?> template, Object... variables) {
		super(template, variables);
		this.entityRootClassType = null;
		this.influencedVariables = Collections.emptySet();
	}

	public Set<Class<? extends IOBIEThing>> getInfluencedVariables() {
		throw new NotImplementedException("getInfluencedVariables is not yet implemented");
//		return influencedVariables;
	}

	public Class<? extends IOBIEThing> getEntityRootClassType() {
		throw new NotImplementedException("getEntityRootClassType is not yet implemented");
//		return entityRootClassType;
	}

}
