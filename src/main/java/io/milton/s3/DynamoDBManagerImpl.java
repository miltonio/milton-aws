/*
 * Copyright (C) McEvoy Software Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.milton.s3;

import io.milton.s3.db.DynamoDBService;
import io.milton.s3.db.DynamoDBServiceImpl;
import io.milton.s3.db.mapper.DynamoDBEntityMapper;
import io.milton.s3.model.Entity;
import io.milton.s3.model.Folder;
import io.milton.s3.util.AttributeKey;
import io.milton.s3.util.DateUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.regions.Region;
import com.amazonaws.services.dynamodbv2.model.AttributeAction;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.DeleteItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;

public class DynamoDBManagerImpl implements DynamoDBManager {
	
	/**
     * Amazon DynamoDB Storage Service
     */
    private final DynamoDBService dynamoDBService;
    
    /**
     * Initialize Amazon DynamoDB environment for the given tableName
     * 
     * @param region
     *            - You can choose the geographical Region where Amazon S3 will
     *            store the buckets you create
     * @param tableName
     *            - Table name
     */
	public DynamoDBManagerImpl(Region region) {
		dynamoDBService = new DynamoDBServiceImpl(region);
	}
	
	/**
	 * Create table for the given tableName in the Amazon DynamoDB
	 * 
	 * @param tableName
	 *             - Table name
	 */
	@Override
	public boolean createTable(String tableName) {
		boolean isTableExist = dynamoDBService.isTableExist(tableName);
	    if (!isTableExist) {
            // Create table if it's not exist & describe the table for the given
            // table after created
            return dynamoDBService.createTable(tableName);
        }
	    return isTableExist;
	}
	
	@Override
    public boolean deleteTable(String tableName) {
        return dynamoDBService.deleteTable(tableName);
    }
	
	@Override
    public boolean isExistEntity(String tableName, String entityName, Folder parent) {
        if (StringUtils.isEmpty(entityName)) {
            return false;
        }
        
        Map<String, Condition> conditions = new HashMap<String, Condition>();
        String parentId = AttributeKey.NOT_EXIST;
        if (parent != null) {
            parentId = parent.getId().toString();
        }
        
        // Search entity by parent unique UUID
        Condition parentUniqueId = new Condition().withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withS(parentId));
        conditions.put(AttributeKey.PARENT_UUID, parentUniqueId);
        
        // Search entity by name
        Condition entityKeyName = new Condition().withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withS(entityName));
        conditions.put(AttributeKey.ENTITY_NAME, entityKeyName);
        
        List<Map<String, AttributeValue>> items = dynamoDBService.getItem(tableName, conditions);
        List<Entity> children = DynamoDBEntityMapper.convertItemsToEntities(parent, items);
        if (children == null || children.isEmpty()) {
            return false;
        }
        
        return true;
    }
	
	/**
	 * The putEntity method stores an item in a table
	 * 
	 * @param entity
	 * @return
	 */
	@Override
	public boolean putEntity(String tableName, Entity entity) {
		Map<String, AttributeValue> newItem = dynamoDBService.newItem(entity);
		PutItemResult putItemResult = dynamoDBService.putItem(tableName, newItem);
		if (putItemResult != null) {
		    return true;
		}
		
		return false;
	}
	
	/**
	 * The findRootFolder method retrieves an root item
	 * 
	 * @return
	 */
	@Override
	public Folder findRootFolder(String tableName) {
        Condition condition = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(AttributeKey.NOT_EXIST));

        Map<String, Condition> conditions = new HashMap<String, Condition>();
        conditions.put(AttributeKey.PARENT_UUID, condition);

        List<Map<String, AttributeValue>> items = dynamoDBService.getItem(tableName, conditions);
        List<Entity> children = DynamoDBEntityMapper.convertItemsToEntities(null, items);
        if (children == null || children.isEmpty()) {
            return null;
        }

        return (Folder) children.get(0);
	}
	
	/**
	 * The findEntityByUniqueId method retrieves an root item for the given
	 * unique UUID
	 * 
	 * @param entity
	 * @return Entity
	 */
	@Override
	public Entity findEntityByUniqueId(String tableName, Entity entity) {
		if (entity == null) {
		    return null;
		}
		
		HashMap<String, AttributeValue> primaryKey = new HashMap<String, AttributeValue> ();
		primaryKey.put(AttributeKey.UUID, new AttributeValue().withS(entity.getId().toString()));
		Map<String, AttributeValue> items = dynamoDBService.getItem(tableName, primaryKey);
		return DynamoDBEntityMapper.convertItemToEntity(entity.getParent(), items);
	}
	
	
	/**
	 * The findEntityByUniqueId method retrieves an root item for the given
	 * unique UUID & parent
	 * 
	 * @param uniqueId
	 * @param parent
	 * 
	 * @return Entity
	 */
	@Override
	public Entity findEntityByUniqueId(String tableName, String uniqueId, Folder parent) {
		if (StringUtils.isEmpty(uniqueId)) {
		    return null;
		}
		
		HashMap<String, AttributeValue> primaryKey = new HashMap<String, AttributeValue> ();
		primaryKey.put(AttributeKey.UUID, new AttributeValue().withS(uniqueId));
		Map<String, AttributeValue> items = dynamoDBService.getItem(tableName, primaryKey);
		return DynamoDBEntityMapper.convertItemToEntity(parent, items);
	}
	
	/**
	 * The findEntityByParent method enables you to retrieve multiple items
	 * from one table.
	 * 
	 * @param parent
	 * @return
	 */
	@Override
	public List<Entity> findEntityByParent(String tableName, Folder parent) {
		if (parent == null) {
			return Collections.emptyList();
		}
		
		Condition condition = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
            .withAttributeValueList(new AttributeValue().withS(parent.getId().toString()));
        Map<String, Condition> conditions = new HashMap<String, Condition>();
        conditions.put(AttributeKey.PARENT_UUID, condition);
        
        List<Map<String, AttributeValue>> items = dynamoDBService.getItem(tableName, conditions);
        List<Entity> children = DynamoDBEntityMapper.convertItemsToEntities(parent, items);
        if (children == null || children.isEmpty()) {
            return Collections.emptyList();
        }
        
        return children;
	}
	
	/**
     * The findEntityByParentAndType method enables you to retrieve multiple items
     * from one table.
     * 
     * @param parent
     * @param isDirectory
     * @return a list of entities
     */
	@Override
    public List<Entity> findEntityByParentAndType(String tableName, Folder parent, boolean isDirectory) {
	    if (parent == null) {
            return Collections.emptyList();
        }
	    
	    Map<String, Condition> conditions = new HashMap<String, Condition>();
	    Condition parentUniqueId = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
	            .withAttributeValueList(new AttributeValue().withS(parent.getId().toString()));
        conditions.put(AttributeKey.PARENT_UUID, parentUniqueId);
        
        Condition entityType = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withN(Integer.toString(isDirectory ? 1 : 0)));
        conditions.put(AttributeKey.IS_DIRECTORY, entityType);
	    
        List<Map<String, AttributeValue>> items = dynamoDBService.getItem(tableName, conditions);
        List<Entity> children = DynamoDBEntityMapper.convertItemsToEntities(parent, items);
        if (children == null || children.isEmpty()) {
            return Collections.emptyList();
        }
        
        return children;
    }
	
	/**
	 * Move or rename entity to other folder
	 * 
	 * @param entity
	 * 				- current entity want to move or rename
	 * @param newParent
	 * 				- parent of entity
	 * @param newEntityName
	 * 				- new name of entity
	 * @param isRenamingAction
	 * 				- TRUE is renaming file, otherwise FALSE
	 * @return TRUE/FALSE
	 */
	@Override
	public boolean updateEntityByUniqueId(String tableName, Entity entity, Folder newParent, 
	        String newEntityName, boolean isRenamingAction) {
		HashMap<String, AttributeValue> primaryKey = new HashMap<String, AttributeValue>();
        primaryKey.put(AttributeKey.UUID, new AttributeValue().withS(entity.getId().toString()));
        
        Map<String, AttributeValueUpdate> updateItems = new HashMap<String, AttributeValueUpdate>();
        updateItems.put(AttributeKey.ENTITY_NAME, new AttributeValueUpdate()
        	.withAction(AttributeAction.PUT).withValue(new AttributeValue().withS(newEntityName)));
        updateItems.put(AttributeKey.MODIFIED_DATE, new AttributeValueUpdate()
    		.withAction(AttributeAction.PUT).withValue(new AttributeValue().withS(DateUtils.dateToString(new Date()))));
        
        if (!isRenamingAction) {
        	updateItems.put(AttributeKey.PARENT_UUID, new AttributeValueUpdate()
    			.withAction(AttributeAction.PUT).withValue(new AttributeValue()
    			.withS(newParent.getId().toString())));
        }
        
        UpdateItemResult updateStatus = dynamoDBService.updateItem(tableName, primaryKey, updateItems);
        if (updateStatus != null) {
            return true;
        }
        
		return false;
	}
	
	/**
	 * The deleteEntityByUniqueId method deletes an item from a table.
	 * 
	 * @param uniqueId
	 * @return
	 */
	@Override
	public boolean deleteEntityByUniqueId(String tableName, String uniqueId) {
		if (StringUtils.isEmpty(uniqueId)) {
		    return false;
		}
		
		HashMap<String, AttributeValue> primaryKey = new HashMap<String, AttributeValue> ();
		primaryKey.put(AttributeKey.UUID, new AttributeValue().withS(uniqueId));
		DeleteItemResult deleteItemResult = dynamoDBService.deleteItem(tableName, primaryKey);
		if (deleteItemResult != null) {
		    return true;
		}
		
		return false;
	}

}
