#Enable kafka buffer
Add this rule to UNOMI POST:/cxs/rules
```json
{
  "metadata": {
    "id": "global-DataOperationEvent",
    "name": "Send event to kafka",
    "description": "Send event to kafka for later processing",
    "readOnly":false
  },
  "condition": 
        {
          "type": "notSystemEventCondition",
        },
  "actions": [
    {
      "type": "bufferEventProcessing"
    }
  ]
}
```