package com.sbxcloud.library.kotlin.core

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.json

/**
 * Created by lgguzman on 6/11/17.
 */
class SbxQuery {


    val parser: Parser = Parser()
    var q = JsonObject().apply {
        put("page", 1)
        put("size", 1000)
    }
    var group = JsonObject().apply {
        put("group", "AND")
        put("GROUP", JsonArray<Any?>())
    }
    //var q: JsonObject = parser.parse(StringBuilder("  { \"page\": 1, \"size\": 1000, \"where\": []}")) as JsonObject
    //var group: JsonObject = parser.parse(StringBuilder("  { \"group\": \"AND\", \"GROUP\": []}")) as JsonObject
    var OP = json{ arrayOf("in", "IN", "not in", "NOT IN", "is", "IS", "is not", "IS NOT", "<>", "!=", "=", "<", "<=", ">=", ">", "like", "LIKE")}

    fun setDomain (domainId: Int): SbxQuery {

        q["domain"] = domainId;
        return this;
    }

     fun setModel(modelName: String): SbxQuery {
        q["row_model"] = modelName;
        return this;
    }

    fun setPage(page: Int): SbxQuery {
        q["page"] = page;
        return this;
    }

     fun setPageSize(pageSize: Int): SbxQuery {
        q["size"] = pageSize;
        return this;
    }

     fun fetchModels(arrayOfModelNames: Array<String>): SbxQuery {
        q["fetch"] = JsonArray(arrayOfModelNames.toList())
        return this;
    }


    @Suppress("UNCHECKED_CAST")
    fun  addObjectArray(array: JsonArray<JsonObject>): SbxQuery {

        q["where"] = null
        if (q["rows"]==null) {
            q["rows"] = JsonArray<JsonObject>()
        }

        (q["rows"] as JsonArray<JsonObject>).addAll(array)
        return this;
    }

    @Suppress("UNCHECKED_CAST")
     fun addObject(data: JsonObject): SbxQuery {
        q["where"] = null
        if (q["rows"]==null) {
            q["rows"] =  JsonArray<JsonObject>();
        }

         (q["rows"] as JsonArray<JsonObject>).add(data);
        return this;
    }

     fun whereWithKeys(keysArray: Array<String>): SbxQuery {
         q["where"]  = JsonObject()
         (q["where"] as JsonObject)["keys"] = keysArray
        return this;
    }

    @Suppress("UNCHECKED_CAST")
     fun newGroup(connectorANDorOR: String): SbxQuery {

        q["rows"] = null;

        // override array where
        if (!(q["where"] is JsonArray<*>)) {
            q["where"] = JsonArray<Any?>();
        }

        if ((group["GROUP"] as JsonArray<*>).size > 0) {
            (q["where"] as JsonArray<Any?>).add(group)
        }

        group =  JsonObject()
        group["ANDOR"] = connectorANDorOR
        group["GROUP"] = JsonArray<Any?>()

        return this;
    }

    fun orderBy (field: String, asc: Boolean): SbxQuery {
        var temp = if (!asc) false else asc
        q["order_by"] = JsonObject()
        (q["order_by"] as JsonObject)["ASC"] = temp
        (q["order_by"] as JsonObject)["FIELD"] = field
        return this;
    }

//     fun setReferenceJoin(operator, filter_field, reference_field, model, value) {
//        q.reference_join = {
//            "row_model": model,
//            "filter": {
//            "OP": operator,
//            "VAL": value,
//            "FIELD": filter_field
//        },
//            "reference_field": reference_field
//        }
//        return self;
//    }

    @Suppress("UNCHECKED_CAST")
     fun addCondition(connectorANDorOR: String, fieldName: String, operator:String , value: Any?): SbxQuery {
        // override array where

         var myConnector =connectorANDorOR
        if (q["where"] is  JsonArray<*>) {
            q["where"] = JsonArray<Any?>();
        }

        // first connector is ALWAYS AND
        if ((group["GROUP"] as JsonArray<Any?>).size < 1) {
            myConnector = "AND";
        }

        // allow only letters and '.' in the fields.
//        if (/^[a-zA-Z0-9\._-]+$/.test(fieldName) == false) {
//            throw new Error("Invalid FIELD NAME: " + fieldName)
//        }

        // check if the user is using valid operators.
        if (OP.indexOf(operator) < 0) {
            throw  Error("Invalid operator: " + operator)
        }

         (group["GROUP"]as JsonArray<Any?>).add(
                 JsonObject().apply{
            put("ANDOR", myConnector)
            put("FIELD", fieldName)
            put("OP", operator)
            put("VAL", value)
        });

        return this
    }

     fun compile(): String {

        if (q["where"]!=null) {
             q["rows"]=null
            q.remove("rows")

            if (q["where"] is JsonArray<*> && (group["GROUP"] as JsonArray<Any?>).size > 0) {
                (q["where"] as JsonArray<Any?>).add(group)
            }
        } else if (q["rows"]!=null) {
             q.remove("where")
        }

        return q.toJsonString()
    }

}
