#Enable kafka buffer
Add this rule to UNOMI POST:/cxs/rules
```json
{
    "metadata": {
        "id": "bufferEvent",
        "name": "bufferEvent Event",
        "description": "bufferEvent Event",
        "readOnly": false
    },
    "condition": {
        "type": "booleanCondition",
        "parameterValues": {
            "subConditions": [
                {
                    "type": "eventPropertyCondition",
                    "parameterValues": {
                        "propertyName": "scope",
                        "propertyValue": "systemscope",
                        "comparisonOperator": "notEquals"
                    }
                },
                {
                    "type": "eventPropertyCondition",
                    "parameterValues": {
                        "propertyName": "eventType",
                        "propertyValue": "ruleFired",
                        "comparisonOperator": "notEquals"
                    }
                }
            ],
            "operator": "and"
        }
    },
    "actions": [
        {
            "type": "bufferEventProcessing"
        }
    ]
}
```