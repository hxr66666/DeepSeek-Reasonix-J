parser grammar JavaParser;

@header { package com.reasonix.code.antlr; }

options {
    tokenVocab = JavaLexer;
    superClass = JavaParserBase;
}

compilationUnit
    : packageDeclaration? (importDeclaration | ';')* (typeDeclaration | ';')* EOF
    | modularCompulationUnit EOF
    ;

modularCompulationUnit
    : importDeclaration* moduleDeclaration
    ;

packageDeclaration
    : annotation* PACKAGE qualifiedName ';'
    ;

importDeclaration
    : IMPORT STATIC? qualifiedName ('.' '*')? ';'
    ;

typeDeclaration
    : classOrInterfaceModifier* (
        classDeclaration
        | enumDeclaration
        | interfaceDeclaration
        | annotationTypeDeclaration
        | recordDeclaration
    )
    ;

modifier
    : classOrInterfaceModifier
    | NATIVE
    | SYNCHRONIZED
    | TRANSIENT
    | VOLATILE
    ;

classOrInterfaceModifier
    : annotation
    | PUBLIC
    | PROTECTED
    | PRIVATE
    | STATIC
    | ABSTRACT
    | FINAL
    | STRICTFP
    | SEALED
    | NON_SEALED
    ;

variableModifier
    : FINAL
    | annotation
    ;

classDeclaration
    : CLASS identifier typeParameters? (EXTENDS typeType)? (IMPLEMENTS typeList)? (
        PERMITS typeList
    )?
    classBody
    ;

typeParameters
    : '<' typeParameter (',' typeParameter)* '>'
    ;

typeParameter
    : annotation* identifier (EXTENDS annotation* typeBound)?
    ;

typeBound
    : typeType ('&' typeType)*
    ;

enumDeclaration
    : ENUM identifier (IMPLEMENTS typeList)? '{' enumConstants? ','? enumBodyDeclarations? '}'
    ;

enumConstants
    : enumConstant (',' enumConstant)*
    ;

enumConstant
    : annotation* identifier arguments? classBody?
    ;

enumBodyDeclarations
    : ';' classBodyDeclaration*
    ;

interfaceDeclaration
    : INTERFACE identifier typeParameters? (EXTENDS typeList)? (PERMITS typeList)? interfaceBody
    ;

classBody
    : '{' classBodyDeclaration* '}'
    ;

interfaceBody
    : '{' interfaceBodyDeclaration* '}'
    ;

classBodyDeclaration
    : ';'
    | STATIC? block
    | modifier* memberDeclaration
    ;

memberDeclaration
    : recordDeclaration
    | methodDeclaration
    | genericMethodDeclaration
    | fieldDeclaration
    | constructorDeclaration
    | genericConstructorDeclaration
    | interfaceDeclaration
    | annotationTypeDeclaration
    | classDeclaration
    | enumDeclaration
    ;

methodDeclaration
    : typeTypeOrVoid identifier formalParameters ('[' ']')* (THROWS qualifiedNameList)? methodBody
    ;

methodBody
    : block
    | ';'
    ;

typeTypeOrVoid
    : typeType
    | VOID
    ;

genericMethodDeclaration
    : typeParameters methodDeclaration
    ;

genericConstructorDeclaration
    : typeParameters constructorDeclaration
    ;

constructorDeclaration
    : identifier formalParameters (THROWS qualifiedNameList)? constructorBody = block
    ;

compactConstructorDeclaration
    : modifier* identifier constructorBody = block
    ;

fieldDeclaration
    : typeType variableDeclarators ';'
    ;

interfaceBodyDeclaration
    : modifier* interfaceMemberDeclaration
    | ';'
    ;

interfaceMemberDeclaration
    : recordDeclaration
    | constDeclaration
    | interfaceMethodDeclaration
    | genericInterfaceMethodDeclaration
    | interfaceDeclaration
    | annotationTypeDeclaration
    | classDeclaration
    | enumDeclaration
    ;

constDeclaration
    : typeType constantDeclarator (',' constantDeclarator)* ';'
    ;

constantDeclarator
    : identifier ('[' ']')* '=' variableInitializer
    ;

interfaceMethodDeclaration
    : interfaceMethodModifier* interfaceCommonBodyDeclaration
    ;

interfaceMethodModifier
    : annotation
    | PUBLIC
    | ABSTRACT
    | DEFAULT
    | STATIC
    | STRICTFP
    ;

genericInterfaceMethodDeclaration
    : interfaceMethodModifier* typeParameters interfaceCommonBodyDeclaration
    ;

interfaceCommonBodyDeclaration
    : annotation* typeTypeOrVoid identifier formalParameters ('[' ']')* (THROWS qualifiedNameList)? methodBody
    ;

variableDeclarators
    : variableDeclarator (',' variableDeclarator)*
    ;

variableDeclarator
    : variableDeclaratorId ('=' variableInitializer)?
    ;

variableDeclaratorId
    : identifier ('[' ']')*
    ;

variableInitializer
    : arrayInitializer
    | expression
    ;

arrayInitializer
    : '{' (variableInitializer (',' variableInitializer)* ','?)? '}'
    ;

classType:
    (
      ( packageName '.' annotation* )? typeIdentifier typeArguments?
    )+ ( '.' annotation* typeIdentifier typeArguments? )*
    ;

packageName:
    identifier ('.' identifier)*
    ;

typeArgument
    : typeType
    | annotation* '?' ((EXTENDS | SUPER) typeType)?
    ;

qualifiedNameList
    : qualifiedName (',' qualifiedName)*
    ;

formalParameters
    : '(' (
       ( receiverParameter | formalParameter ) (',' formalParameterList)*
    )? ')'
    ;

receiverParameter
    : typeType (identifier '.')* THIS
    ;

formalParameterList
    : formalParameter (',' formalParameter)*
    ;

formalParameter
    : variableModifier* typeType (annotation* '...')? variableDeclaratorId
    ;

lambdaLVTIList
    : lambdaLVTIParameter (',' lambdaLVTIParameter)*
    ;

lambdaLVTIParameter
    : variableModifier* VAR identifier
    ;

qualifiedName
    : identifier ('.' identifier)*
    ;

literal
    : integerLiteral
    | floatLiteral
    | CHAR_LITERAL
    | STRING_LITERAL
    | BOOL_LITERAL
    | NULL_LITERAL
    | TEXT_BLOCK
    ;

integerLiteral
    : DECIMAL_LITERAL
    | HEX_LITERAL
    | OCT_LITERAL
    | BINARY_LITERAL
    ;

floatLiteral
    : FLOAT_LITERAL
    | HEX_FLOAT_LITERAL
    ;

annotation :
    ('@' qualifiedName) annotationFieldValues?
    ;

annotationFieldValues:
	'(' ( annotationFieldValue ( ',' annotationFieldValue )* )? ')'
	;

annotationFieldValue:
	{ this.IsNotIdentifierAssign() }? annotationValue
	| identifier '=' annotationValue
	;

annotationValue:
	expression
	| annotation
	| '{' ( annotationValue ( ',' annotationValue )* )? ','? '}'
	;

elementValue
    : expression
    | annotation
    | elementValueArrayInitializer
    ;

elementValueArrayInitializer
    : '{' (elementValue (',' elementValue)*)? ','? '}'
    ;

annotationTypeDeclaration
    : '@' INTERFACE identifier annotationTypeBody
    ;

annotationTypeBody
    : '{' annotationTypeElementDeclaration* '}'
    ;

annotationTypeElementDeclaration
    : modifier* annotationTypeElementRest
    | ';'
    ;

annotationTypeElementRest
    : typeType annotationMethodOrConstantRest ';'
    | classDeclaration ';'?
    | interfaceDeclaration ';'?
    | enumDeclaration ';'?
    | annotationTypeDeclaration ';'?
    | recordDeclaration ';'?
    ;

annotationMethodOrConstantRest
    : annotationMethodRest
    | annotationConstantRest
    ;

annotationMethodRest
    : identifier '(' ')' defaultValue?
    ;

annotationConstantRest
    : variableDeclarators
    ;

defaultValue
    : DEFAULT elementValue
    ;

moduleDeclaration
    : annotation* OPEN? MODULE qualifiedName '{' moduleDirective* '}'
    ;

moduleDirective
    : REQUIRES requiresModifier* qualifiedName ';'
    | EXPORTS qualifiedName (TO qualifiedName (',' qualifiedName)* )? ';'
    | OPENS qualifiedName (TO qualifiedName (',' qualifiedName)* )? ';'
    | USES qualifiedName ';'
    | PROVIDES qualifiedName WITH qualifiedName (',' qualifiedName)* ';'
    ;

requiresModifier
    : TRANSITIVE
    | STATIC
    ;

recordDeclaration
    : RECORD identifier typeParameters? recordHeader (IMPLEMENTS typeList)? recordBody
    ;

recordHeader
    : '(' recordComponentList? ')'
    ;

recordComponentList
    : recordComponent (',' recordComponent)* { this.DoLastRecordComponent() }?
    ;

recordComponent
    : annotation* typeType (annotation* ELLIPSIS)? identifier
    ;

recordBody
    : '{' (classBodyDeclaration | compactConstructorDeclaration)* '}'
    ;

block
    : '{' blockStatement* '}'
    ;

blockStatement
    : localVariableDeclaration ';'
    | localTypeDeclaration
    | statement
    ;

localVariableDeclaration
    : variableModifier* (VAR identifier '=' expression | typeType variableDeclarators)
    ;

identifier
    : IDENTIFIER
    | MODULE
    | OPEN
    | REQUIRES
    | EXPORTS
    | OPENS
    | TO
    | USES
    | PROVIDES
    | WHEN
    | WITH
    | TRANSITIVE
    | YIELD
    | SEALED
    | PERMITS
    | RECORD
    | VAR
    ;

typeIdentifier
    : IDENTIFIER
    | MODULE
    | OPEN
    | REQUIRES
    | EXPORTS
    | OPENS
    | TO
    | USES
    | PROVIDES
    | WITH
    | TRANSITIVE
    | SEALED
    ;

localTypeDeclaration
    : classOrInterfaceModifier* (classDeclaration | interfaceDeclaration | recordDeclaration | enumDeclaration)
    ;

statement
    : blockLabel = block
    | ASSERT expression (':' expression)? ';'
    | IF '(' expression ')' statement (ELSE statement)?
    | FOR '(' forControl ')' statement
    | WHILE '(' expression ')' statement
    | DO statement WHILE '(' expression ')' ';'
    | TRY block (catchClause+ finallyBlock? | finallyBlock)
    | TRY resourceSpecification block catchClause* finallyBlock?
    | SWITCH '(' expression ')' '{' switchBlockStatementGroup* switchLabel* '}'
    | SYNCHRONIZED '(' expression ')' block
    | RETURN expression? ';'
    | THROW expression ';'
    | BREAK identifier? ';'
    | CONTINUE identifier? ';'
    | YIELD expression ';'
    | SEMI
    | statementExpression = expression ';'
    | switchExpression ';'?
    | identifierLabel = identifier ':' statement
    ;

catchClause
    : CATCH '(' variableModifier* catchType identifier ')' block
    ;

catchType
    : qualifiedName ('|' qualifiedName)*
    ;

finallyBlock
    : FINALLY block
    ;

resourceSpecification
    : '(' resources ';'? ')'
    ;

resources
    : resource (';' resource)*
    ;

resource
    : variableModifier* (classOrInterfaceType variableDeclaratorId | VAR identifier) '=' expression
    | qualifiedName
    ;

switchBlockStatementGroup
    : (switchLabel ':')+ blockStatement+
    ;

switchLabel
    : CASE (
        constantExpression = expression
        | enumConstantName = IDENTIFIER
        | typeType varName = identifier
    )
    | DEFAULT
    ;

forControl
    : enhancedForControl
    | forInit? ';' expression? ';' forUpdate = expressionList?
    ;

forInit
    : localVariableDeclaration
    | expressionList
    ;

enhancedForControl
    : variableModifier* (typeType | VAR) variableDeclaratorId ':' expression
    ;

expressionList
    : expression (',' expression)*
    ;

methodCall
    : (identifier | THIS | SUPER) arguments
    ;

expression
    : primary                                                       #PrimaryExpression
    | expression '[' expression ']'                                 #SquareBracketExpression
    | expression bop = '.' (
        identifier
        | methodCall
        | THIS
        | NEW nonWildcardTypeArguments? innerCreator
        | SUPER superSuffix
        | explicitGenericInvocation
    )                                                               #MemberReferenceExpression
    | methodCall                                                    #MethodCallExpression
    | expression '::' typeArguments? identifier                     #MethodReferenceExpression
    | typeType '::' (typeArguments? identifier | NEW)               #MethodReferenceExpression
    | classType '::' typeArguments? NEW                             #MethodReferenceExpression
    | switchExpression                                              #ExpressionSwitch
    | expression postfix = ('++' | '--')                            #PostIncrementDecrementOperatorExpression
    | prefix = ('+' | '-' | '++' | '--' | '~' | '!') expression     #UnaryOperatorExpression
    | '(' annotation* typeType ('&' typeType)* ')' expression       #CastExpression
    | NEW creator                                                   #ObjectCreationExpression
    | expression bop = ('*' | '/' | '%') expression           #BinaryOperatorExpression
    | expression bop = ('+' | '-') expression                 #BinaryOperatorExpression
    | expression ('<' '<' | '>' '>' '>' | '>' '>') expression #BinaryOperatorExpression
    | expression bop = ('<=' | '>=' | '>' | '<') expression   #BinaryOperatorExpression
    | expression bop = INSTANCEOF (typeType | pattern)        #InstanceOfOperatorExpression
    | expression bop = ('==' | '!=') expression               #BinaryOperatorExpression
    | expression bop = '&' expression                         #BinaryOperatorExpression
    | expression bop = '^' expression                         #BinaryOperatorExpression
    | expression bop = '|' expression                         #BinaryOperatorExpression
    | expression bop = '&&' expression                        #BinaryOperatorExpression
    | expression bop = '||' expression                        #BinaryOperatorExpression
    | <assoc = right> expression bop = '?' expression ':' expression #TernaryExpression
    | <assoc = right> expression bop = (
        '='
        | '+='
        | '-='
        | '*='
        | '/='
        | '&='
        | '|='
        | '^='
        | '>>='
        | '>>>='
        | '<<='
        | '%='
    ) expression                                              #BinaryOperatorExpression
    | lambdaExpression                                        #ExpressionLambda
    ;

pattern
    : variableModifier* typeType annotation* variableDeclarators
    | typeType '(' componentPatternList? ')'
    ;

componentPatternList :
    componentPattern ( ',' componentPattern )*
    ;

componentPattern :
    pattern
    ;

lambdaExpression
    : lambdaParameters '->' lambdaBody
    ;

lambdaParameters
    : identifier
    | '(' formalParameterList? ')'
    | '(' identifier (',' identifier)* ')'
    | '(' lambdaLVTIList? ')'
    ;

lambdaBody
    : expression
    | block
    ;

primary
    : '(' expression ')'
    | THIS
    | SUPER
    | literal
    | identifier
    | typeTypeOrVoid '.' CLASS
    | nonWildcardTypeArguments (explicitGenericInvocationSuffix | THIS arguments)
    ;

switchExpression
    : SWITCH '(' expression ')' '{' switchLabeledRule* '}'
    ;

switchLabeledRule
    : CASE (
	expressionList
	| NULL_LITERAL (',' DEFAULT)?
	| casePattern (',' casePattern)* guard?
	) (ARROW | COLON) switchRuleOutcome
    | DEFAULT (ARROW | COLON) switchRuleOutcome
    ;

guard 
    : WHEN expression
    ;

casePattern
    : pattern
    ;

switchRuleOutcome
    : block
    | blockStatement*
    ;

classOrInterfaceType
    : classType
    ;

creator
    : nonWildcardTypeArguments? createdName classCreatorRest
    | createdName arrayCreatorRest
    ;

createdName
    : identifier typeArgumentsOrDiamond? ('.' identifier typeArgumentsOrDiamond?)*
    | primitiveType
    ;

innerCreator
    : identifier nonWildcardTypeArgumentsOrDiamond? classCreatorRest
    ;

arrayCreatorRest
    : ('[' ']')+ arrayInitializer
    | ('[' expression ']')+ ('[' ']')*
    ;

classCreatorRest
    : arguments classBody?
    ;

explicitGenericInvocation
    : nonWildcardTypeArguments explicitGenericInvocationSuffix
    ;

typeArgumentsOrDiamond
    : '<' '>'
    | typeArguments
    ;

nonWildcardTypeArgumentsOrDiamond
    : '<' '>'
    | nonWildcardTypeArguments
    ;

nonWildcardTypeArguments
    : '<' typeList '>'
    ;

typeList
    : typeType (',' typeType)*
    ;

typeType
    : annotation* (classOrInterfaceType | primitiveType) (annotation* '[' ']')*
    ;

primitiveType
    : BOOLEAN
    | CHAR
    | BYTE
    | SHORT
    | INT
    | LONG
    | FLOAT
    | DOUBLE
    ;

typeArguments
    : '<' typeArgument (',' typeArgument)* '>'
    ;

superSuffix
    : arguments
    | '.' typeArguments? identifier arguments?
    ;

explicitGenericInvocationSuffix
    : SUPER superSuffix
    | identifier arguments
    ;

arguments
    : '(' expressionList? ')'
    ;
